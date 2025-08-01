package com.sunmiprinterlibrary

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.RemoteException
import android.util.Base64
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableMap
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.InnerResultCallback
import com.sunmi.peripheral.printer.SunmiPrinterService
import com.sunmi.peripheral.printer.WoyouConsts

//SUNMI PAYMENTS
import EmvUtil

import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2
import com.sunmi.pay.hardware.aidlv2.pinpad.PinPadOptV2
import com.sunmi.pay.hardware.aidlv2.readcard.ReadCardOptV2
import com.sunmi.pay.hardware.aidlv2.security.SecurityOptV2
import com.sunmi.pay.hardware.aidlv2.system.BasicOptV2

import com.sunmiprinterlibrary.Callback.CheckCardCallback
import com.sunmiprinterlibrary.Callback.EMVCallback
import com.sunmiprinterlibrary.Callback.PinPadCallback

//import com.sunmiprinterlibrary.databinding.ActivityMainBinding

import com.sunmiprinterlibrary.util.ByteUtil
import com.sunmiprinterlibrary.util.TLV
import com.sunmiprinterlibrary.util.TLVUtil

import com.sunmi.pay.hardware.aidl.AidlConstants
import com.sunmi.pay.hardware.aidl.AidlConstants.EMV.TLVOpCode
import com.sunmi.pay.hardware.aidl.AidlErrorCode
import com.sunmi.pay.hardware.aidlv2.bean.EMVCandidateV2
import com.sunmi.pay.hardware.aidlv2.bean.EMVTransDataV2
import com.sunmi.pay.hardware.aidlv2.bean.PinPadConfigV2

//import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2
//import com.sunmi.pay.hardware.aidlv2.pinpad.PinPadOptV2
import com.sunmi.pay.hardware.aidlv2.readcard.CheckCardCallbackV2
//import com.sunmi.pay.hardware.aidlv2.readcard.ReadCardOptV2

import sunmi.paylib.SunmiPayKernel

import android.util.Log

import androidx.lifecycle.MutableLiveData

import android.annotation.SuppressLint
import java.nio.charset.StandardCharsets
import android.os.Bundle
import android.os.Handler
import android.os.Looper

class SunmiPrinterLibraryModule(reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  private var printerService: SunmiPrinterService? = null

  //SUNMI PAYMENTS
  private var mSMPayKernel: SunmiPayKernel? = null
  //FOR UI
  private val cardType = MutableLiveData<String>()
  private val result = MutableLiveData<String>()

  private val amount = "40000"
  private var mCardNo: String = ""
  private var mCardType = 0
  private var mPinType: Int? = null
  private var mCertInfo: String = ""

  init {}

  override fun getName(): String {
    return NAME
  }

  companion object {
    const val NAME = "SunmiPrinterLibrary"

    //SUNMIN PAYMENTS
    var mBasicOptV2: BasicOptV2? = null
    var mReadCardOptV2: ReadCardOptV2? = null
    var mEMVOptV2: EMVOptV2? = null
    var mPinPadOptV2: PinPadOptV2? = null
    var mSecurityOptV2: SecurityOptV2? = null


  }

  //SUNMI PAYMENTS CHECK CARDS
  @ReactMethod
  fun startEMV(promise: Promise) {
    Log.d("dd--","CAlled startEMV")

    try {
      EmvUtil().init()
      Log.d("dd--","EmvUtil init COM SUCESSO")
      checkCard(AidlConstants.CardType.IC.value)

      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("0", "startEMV() is FALHADO. " + e.message)
    }
  }


  private fun checkCard(cardType: Int) {
    try {
      Log.d("dd--","EMVOpvt Abort Transaction")
      mEMVOptV2?.abortTransactProcess()
      mEMVOptV2?.initEmvProcess()
      Log.d("dd--","EMVOpvt InitEMV Process Done...")

      mReadCardOptV2?.checkCard(cardType, mCheckCardCallback, 60)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private val mCheckCardCallback: CheckCardCallbackV2 = object : CheckCardCallback() {
    @SuppressLint("SetTextI18n")
    override fun findICCard(atr: String) {
      super.findICCard(atr)

      Handler(Looper.getMainLooper()).post {
        cardType.value = "Type: IC"
        result.value = "Result: $atr"
        Log.e("dd--", "Type:IC")
        Log.e("dd--", "ID: $atr")


        mCardType = AidlConstants.CardType.IC.value
        transactProcess()
      }
    }

    @SuppressLint("SetTextI18n")
    override fun findMagCard(info: Bundle) {
      super.findMagCard(info)

      val track1 = info.getString("TRACK1")
      val track2 = info.getString("TRACK2")
      val track3 = info.getString("TRACK3")

      Handler(Looper.getMainLooper()).post {

        cardType.value = "Type: Magnetic"
        result.value =
          "Result:\n Track 1: $track1 \nTrack 2: $track2 \nTrack 3: $track3 \n"
        Log.e("dd--", "Type:Magnetic")
        Log.e("dd--", "ID: ${result.value}")


        mCardType = AidlConstants.CardType.MAGNETIC.value
      }
    }

    @SuppressLint("SetTextI18n")
    override fun findRFCard(uuid: String) {
      super.findRFCard(uuid)

      Handler(Looper.getMainLooper()).post {

        cardType.value = "Type: NFC"
        result.value = "Result:\n UUID: $uuid"
        Log.e("dd--", "Type:NFC")
        Log.e("dd--", "ID: $uuid")

        mCardType = AidlConstants.CardType.NFC.value
        transactProcess()

      }
    }

    override fun onError(code: Int, message: String) {
      super.onError(code, message)
      val error = "onError:$message -- $code"
      println("Error : $error")
    }
  }

  private fun transactProcess() {
    Log.e("dd--", "transactProcess")
    try {
      val emvTransData = EMVTransDataV2()
      emvTransData.amount = amount //in cent (9F02)
      emvTransData.flowType = 1 //1 Standard Flow, 2 Simple Flow, 3 QPass
      emvTransData.cardType = mCardType
      mEMVOptV2?.transactProcess(emvTransData, mEMVCallback)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private val mEMVCallback = object : EMVCallback(){

    override fun onWaitAppSelect(p0: MutableList<EMVCandidateV2>?, p1: Boolean) {
      super.onWaitAppSelect(p0, p1)

      Log.e("dd--", "onWaitAppSelect isFirstSelect:$p1")

      //Debit Card might have 2 AID
      //Priority 1 should be 'Debit <MS/VISA>'
      //Priority 2 should be 'ATM'
      p0?.forEach {
        Log.e("dd--", "EMVCandidate:$it")
      }
      //default take 1 priority
      mEMVOptV2?.importAppSelect(0)
    }

    override fun onAppFinalSelect(p0: String?) {
      super.onAppFinalSelect(p0)

      Log.e("dd--", "onAppFinalSelect value:$p0")


      val tags = arrayOf("5F2A", "5F36", "9F33", "9F66")
      val value = arrayOf("0458", "00", "E0F8C8", "B6C0C080")
      mEMVOptV2?.setTlvList(TLVOpCode.OP_NORMAL, tags, value)


      if (p0 != null && p0.isNotEmpty()){
        val isVisa = p0.startsWith("A000000003")
        val isMaster =
          (p0.startsWith("A000000004") || p0.startsWith("A000000005"))

        if (isVisa){
          // VISA(PayWave)
          Log.e("dd--", "detect VISA card")
        }else if(isMaster){

          // MasterCard(PayPass)
          Log.e("dd--", "detect MasterCard card")
          val tagsPayPass = arrayOf(
            "DF8117", "DF8118", "DF8119", "DF811B", "DF811D",
            "DF811E", "DF811F", "DF8120", "DF8121", "DF8122",
            "DF8123", "DF8124", "DF8125", "DF812C"
          )
          val valuesPayPass = arrayOf(
            "E0", "F8", "F8", "30", "02",
            "00", "E8", "F45084800C", "0000000000", "F45084800C",
            "000000000000", "999999999999", "999999999999", "00"
          )
          mEMVOptV2?.setTlvList(TLVOpCode.OP_PAYPASS, tagsPayPass, valuesPayPass)

          //Reader CVM Required Limit (Malaysia => RM250)
          mEMVOptV2?.setTlv(TLVOpCode.OP_PAYPASS,"DF8126","000000025000")

        }

      }
      mEMVOptV2?.importAppFinalSelectStatus(0)
    }

    override fun onConfirmCardNo(p0: String?) {
      super.onConfirmCardNo(p0)
      Log.e("dd--", "onConfirmCardNo cardNo:$p0")
      mCardNo = p0!!
      mEMVOptV2?.importCardNoStatus(0)
    }

    override fun onRequestShowPinPad(p0: Int, p1: Int) {
      super.onRequestShowPinPad(p0, p1)
      Log.e("dd--", "onRequestShowPinPad pinType:$p0 remainTime:$p1")
      // 0 - online pin, 1 - offline pin
      mPinType = p0
      initPidPad()
    }

    override fun onCertVerify(p0: Int, p1: String?) {
      super.onCertVerify(p0, p1)
      Log.e("dd--", "onCertVerify certType:$p0 certInfo:$p1")
      mCertInfo = p1.toString()
      mEMVOptV2?.importCertStatus(p0)
    }

    override fun onOnlineProc() {
      super.onOnlineProc()
      Log.e("dd--", "onOnlineProc")
      try{

        if(mCardType != AidlConstants.CardType.MAGNETIC.value){
          getTlvData()
        }
        importOnlineProcessStatus(0)

      }catch (e:Exception){
        e.printStackTrace()
        importOnlineProcessStatus(-1)
      }

    }

    override fun onTransResult(p0: Int, p1: String?) {
      super.onTransResult(p0, p1)
      //Code = 0 (Success)
      Log.e("dd--", "onTransResult code:$p0 desc:$p1")
    }

  }


  private fun initPidPad(){
    Log.e("dd--", "initPinPad")
    try {
      val pinPadConfig = PinPadConfigV2()
      pinPadConfig.pinPadType = 0
      pinPadConfig.pinType = mPinType!!
      pinPadConfig.isOrderNumKey = true
      val panBytes = mCardNo.substring(mCardNo.length - 13, mCardNo.length - 1)
        .toByteArray(StandardCharsets.US_ASCII)
      pinPadConfig.pan = panBytes
      pinPadConfig.timeout = 60 * 1000 // input password timeout
      pinPadConfig.pinKeyIndex = 12 // pik index (0-19)
      pinPadConfig.maxInput = 12
      pinPadConfig.minInput = 4
      pinPadConfig.keySystem = 0 // 0 - MkSk 1 - DuKpt
      pinPadConfig.algorithmType = 0 // 0 - 3DES 1 - SM4
      mPinPadOptV2?.initPinPad(pinPadConfig, mPinPadCallback)
    } catch (e: Exception) {
      e.printStackTrace()
    }

  }

  private val mPinPadCallback = object: PinPadCallback(){

    override fun onConfirm(p0: Int, p1: ByteArray?) {
      super.onConfirm(p0, p1)
      if (p1 != null) {
        val hexStr = ByteUtil.bytes2HexStr(p1)
        Log.e("dd--", "onConfirm pin block:$hexStr")
        importPinInputStatus(0)
      }else{
        importPinInputStatus(2)
      }
    }

    override fun onCancel() {
      super.onCancel()
      Log.e("dd--", "onCancel")
      importPinInputStatus(1)
    }

    override fun onError(p0: Int) {
      super.onError(p0)
      Log.e("dd--", "onError: ${AidlErrorCode.valueOf(p0).msg}")
      importPinInputStatus(3)
    }

  }

  private fun importPinInputStatus(inputResult: Int) {
    Log.e("dd--", "importPinInputStatus:$inputResult")
    try {
      mEMVOptV2?.importPinInputStatus(mPinType!!, inputResult)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun getTlvData() {
    try {
      val tagList = arrayOf(
        "DF02", "5F34", "9F06", "FF30", "FF31", "95", "9B", "9F36", "9F26",
        "9F27", "DF31", "5A", "57", "5F24", "9F1A", "9F03", "9F33", "9F10", "9F37", "9C",
        "9A", "9F02", "5F2A", "5F36", "82", "9F34", "9F35", "9F1E", "84", "4F", "9F09", "9F41",
        "9F63", "5F20", "9F12", "50"
      )
      //Only Mastercard have this extra tag
      val payPassTags = arrayOf(
        "DF811E",
        "DF812C",
        "DF8118",
        "DF8119",
        "DF811F",
        "DF8117",
        "DF8124",
        "DF8125",
        "9F6D",
        "DF811B",
        "9F53",
        "DF810C",
        "9F1D",
        "DF8130",
        "DF812D",
        "DF811C",
        "DF811D",
        "9F7C"
      )
      val outData = ByteArray(2048)
      val map: MutableMap<String, TLV> = HashMap()
      var len = mEMVOptV2?.getTlvList(TLVOpCode.OP_NORMAL, tagList, outData)
      if (len != null && len > 0) { //if (len > 0) {
        //val hexStr = ByteUtil.bytes2HexStr(outData.copyOf(len))
        val hexStr = ByteUtil.bytes2HexStr(outData.copyOf(len ?: 0))
        map.putAll(TLVUtil.hexStrToTLVMap(hexStr))
      }
      len = mEMVOptV2?.getTlvList(TLVOpCode.OP_PAYPASS, payPassTags, outData)
      if (len != null && len > 0) { //if (len > 0) {
        //val hexStr = ByteUtil.bytes2HexStr(outData.copyOf(len))
        val hexStr = ByteUtil.bytes2HexStr(outData.copyOf(len ?: 0))
        map.putAll(TLVUtil.hexStrToTLVMap(hexStr))
      }

      // https://emvlab.org/emvtags/all/ refer this as TLV data
      // Eg: 5F24 -> Expire date
      // Eg: 5F20 -> Card holder
      var temp = ""
      val set: Set<String> = map.keys
      set.forEach {
        val tlv = map[it]
        temp += if (tlv != null) {
          "$it : ${tlv.value} \n"
        } else {
          "$it : \n"
        }
      }
      Log.e("dd--", "TLV: $temp")

    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun importOnlineProcessStatus(status: Int) {
    Log.e("dd--", "importOnlineProcessStatus status:$status")
    try {
      val tags = arrayOf("71", "72", "91", "8A", "89")
      val values = arrayOf("", "", "", "", "")
      val out = ByteArray(1024)
      val len = mEMVOptV2?.importOnlineProcStatus(status, tags, values, out)
      if (len != null && len > 0) { //if (len < 0) {
        Log.e("dd--", "importOnlineProcessStatus error,code:$len")
      } else {
        val bytes = out.copyOf(len ?: 0)
        val hexStr = ByteUtil.bytes2HexStr(bytes)
        Log.e("dd--", "importOnlineProcessStatus outData:$hexStr")
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }
  /*
  override fun onDestroy() {
    super.onDestroy()
    mReadCardOptV2?.cancelCheckCard()
  }

   */

  //END SUNMI PAYMENTS


  @ReactMethod
  fun connect(promise: Promise) {
    try {
      val callback: InnerPrinterCallback = object : InnerPrinterCallback() {
        override fun onConnected(service: SunmiPrinterService) {
          printerService = service
          promise.resolve(true)
        }
        override fun onDisconnected() {
          printerService = null
          promise.reject("0", "native#connect() is failed.")
        }

      }

      val result = InnerPrinterManager.getInstance().bindService(getReactApplicationContext(), callback)
      if (!result){
        promise.reject("0", "native#connect() is failed.")
      }

      // SUNMI PAYMENTS

      mSMPayKernel = SunmiPayKernel.getInstance()
      Log.e("dd--","SUNMIPAYKERNEL... Waiting callback")
      mSMPayKernel?.initPaySDK(getReactApplicationContext(), object : SunmiPayKernel.ConnectCallback {
        override fun onDisconnectPaySDK() {}
        override fun onConnectPaySDK() {
          Log.e("dd--","OnConnectPAY SDK")
          try {
            mReadCardOptV2 = mSMPayKernel?.mReadCardOptV2
            mEMVOptV2 = mSMPayKernel?.mEMVOptV2
            mPinPadOptV2 = mSMPayKernel?.mPinPadOptV2
            mBasicOptV2 = mSMPayKernel?.mBasicOptV2
            mSecurityOptV2 = mSMPayKernel?.mSecurityOptV2
            Log.e("dd--", "SDK INIT SUCCESSFUL")
            promise.resolve(true)
          } catch (e: Exception) {
            promise.reject("SDKPAY","onConnectPAYSDK FALHOU")
            e.printStackTrace()
          }
        }
      })

    } catch (e: Exception) {
      promise.reject("0", "native#connect() is failed. " + e.message)
    }
  }

  @ReactMethod
  fun disconnect(promise: Promise) {
    try {
      val callback: InnerPrinterCallback = object : InnerPrinterCallback() {
        override fun onConnected(service: SunmiPrinterService) {
          promise.reject("0", "native#disconnect() is failed.")
        }
        override fun onDisconnected() {
          printerService = null
          promise.resolve(true)
        }
      }
      InnerPrinterManager.getInstance().unBindService(getReactApplicationContext(), callback)
    } catch (e: Exception) {
      promise.reject("0", "native#disconnect() is failed. " + e.message)
    }
  }

  @ReactMethod
  fun printerInit(promise: Promise) {
    validatePrinterService(promise)
    try {
      val callback = makeInnerResultCallback(promise, "native#printerInit() is failed.")
      printerService?.printerInit(callback)
    } catch (e: Exception) {
      promise.reject("0", "native#printerInit() is failed. " + e.message)
    }
  }

  @ReactMethod
  fun printerSelfChecking(promise: Promise) {
    validatePrinterService(promise)
    try {
      val callback = makeInnerResultCallback(promise, "native#printSelfChecking() is failed.")
      printerService?.printerSelfChecking(callback)
    } catch (e: Exception) {
      promise.reject("0", "native#printSelfChecking() is failed. " + e.message)
    }
  }

  @ReactMethod
  fun getPrinterInfo(promise: Promise) {
    validatePrinterService(promise)
    try {
      val serialNumber = printerService?.getPrinterSerialNo()?.replace("[\n\r]".toRegex(), "")
      val printerVersion = printerService?.getPrinterVersion()?.replace("[\n\r]".toRegex(), "")
      val serviceVersion = printerService?.getServiceVersion()?.replace("[\n\r]".toRegex(), "")
      val printerModal = printerService?.getPrinterModal()?.replace("[\n\r]".toRegex(), "")
      val paperWidth = if (printerService?.getPrinterPaper() == 1) "58mm" else "80mm"

      val map: WritableMap = Arguments.createMap()
      map.putString("serialNumber", serialNumber)
      map.putString("printerVersion", printerVersion)
      map.putString("serviceVersion", serviceVersion)
      map.putString("printerModal", printerModal)
      map.putString("paperWidth", paperWidth)

      promise.resolve(map)
    } catch (e: Exception) {
      promise.reject("0", "native#getPrinterInfo() is failed. " + e.message)
    }
  }

  @ReactMethod
  fun getPrinterSerialNo(promise: Promise) {
    validatePrinterService(promise)
    try {
      val result = printerService?.getPrinterSerialNo()
      promise.resolve(result)
    } catch (e: Exception) {
      promise.reject("0", "native#getPrinterSerialNo() is failed. " + e.message)
    }
  }

  @ReactMethod
  fun getPrinterVersion(promise: Promise) {
    validatePrinterService(promise)
    try {
      val result = printerService?.getPrinterVersion()
      promise.resolve(result)
    } catch (e: Exception) {
      promise.reject("0", "native#getPrinterVersion() is failed. " + e.message)
    }
  }

  @ReactMethod
  fun getServiceVersion(promise: Promise) {
    validatePrinterService(promise)
    try {
      val result = printerService?.getServiceVersion()
      promise.resolve(result)
    } catch (e: Exception) {
      promise.reject("0", "native#getServiceVersion() is failed. " + e.message)
    }
  }

  @ReactMethod
  fun getPrinterModal(promise: Promise) {
    validatePrinterService(promise)
    try {
      val result = printerService?.getPrinterModal()
      promise.resolve(result)
    } catch (e: Exception) {
      promise.reject("0", "native#getPrinterModal() is failed. " + e.message)
    }
  }

  @ReactMethod
  fun getPrinterPaper(promise: Promise) {
    validatePrinterService(promise)
    try {
      val result0 = printerService?.getPrinterPaper()
      val result1 = if (result0 == 1) "58mm" else "80mm"
      promise.resolve(result1)
    } catch (e: Exception) {
      promise.reject("0", "native#getPrinterPaper() is failed. " + e.message)
    }
  }

  @ReactMethod
  fun getPrintedLength(promise: Promise) {
    validatePrinterService(promise)
    try {
      val callback = makeInnerResultCallback(promise, "native#getPrintedLength() is failed.")
      printerService?.getPrintedLength(callback)
    } catch (e: Exception) {
      promise.reject("0", "native#getPrintedLength() is failed. " + e.message)
    }
  }

  @ReactMethod
  fun updatePrinterState(promise: Promise) {
    validatePrinterService(promise)
    try {
      val result = printerService?.updatePrinterState()
      promise.resolve(result)
    } catch (e: Exception) {
      promise.reject("0", "native#updatePrinterState() is failed. " + e.message)
    }
  }

  @ReactMethod
  fun sendRAWData(base64: String, promise: Promise) {
    validatePrinterService(promise)
    try {
      val callback = makeInnerResultCallback(promise, "native#sendRAWData() is failed.")
      val date = Base64.decode(base64, Base64.DEFAULT);
      printerService?.sendRAWData(date, callback);
    } catch (e: Exception) {
      promise.reject("0", "native#sendRAWData() is failed. " + e.message)
    }
  }

  @ReactMethod
  fun setTextStyle(key: String, value: Boolean, promise: Promise) {
    validatePrinterService(promise)
    try {
      val _key = when (key) {
        "doubleWidth" -> WoyouConsts.ENABLE_DOUBLE_WIDTH
        "doubleHeight" -> WoyouConsts.ENABLE_DOUBLE_HEIGHT
        "bold" -> WoyouConsts.ENABLE_BOLD
        "underline"  -> WoyouConsts.ENABLE_UNDERLINE
        "antiWhite"  -> WoyouConsts.ENABLE_ANTI_WHITE
        "strikethrough"  -> WoyouConsts.ENABLE_STRIKETHROUGH
        "italic"  -> WoyouConsts.ENABLE_ILALIC
        "invert" -> WoyouConsts.ENABLE_INVERT
        else -> null
      }
      val _value = if (value) {
        WoyouConsts.ENABLE
      } else {
        WoyouConsts.DISABLE
      }
      if (_key != null){
        printerService?.setPrinterStyle(_key, _value)
        promise.resolve(true)
      }else{
        promise.reject("0", "native#setTextStyle() is failed. key or value is incorrect.")
      }
    } catch (e: Exception) {
      promise.reject("0", "native#setTextStyle() is failed. " + e.message)
    }
  }

  @ReactMethod
  fun setParagraphStyle(key: String, value: Int, promise: Promise) {
    validatePrinterService(promise)
    try {
      val _key = when (key) {
        "textRightSpacing" -> WoyouConsts.SET_TEXT_RIGHT_SPACING
        "relativePosition" -> WoyouConsts.SET_RELATIVE_POSITION
        "absolutePosition" -> WoyouConsts.SET_ABSOLUATE_POSITION
        "lineSpacing"  -> WoyouConsts.SET_LINE_SPACING
        "leftSpacing"  -> WoyouConsts.SET_LEFT_SPACING
        "strikethroughStyle"  -> WoyouConsts.SET_STRIKETHROUGH_STYLE
        else -> null
      }
      if (_key != null){
        printerService?.setPrinterStyle(_key, value)
        promise.resolve(true)
      } else {
        promise.reject("0", "native#setParagraphStyle() is failed. key is incorrect.")
      }
    } catch (e: Exception) {
      promise.reject("0", "native#setParagraphStyle() is failed. " + e.message)
    }
  }

  @ReactMethod
  fun setAlignment(key: String, promise: Promise) {
    validatePrinterService(promise)
    try {
      val _key = alignmentToInt(key)
      if (_key != null){
        val callback = makeInnerResultCallback(promise, "native#setAlignment is failed.")
        printerService?.setAlignment(_key, callback)
      } else {
        promise.reject("0", "native#setAlignment is failed. key is incorrect.")
      }
    } catch (e: Exception) {
      promise.reject("0", "native#setAlignment is failed. " + e.message)
    }
  }

  @ReactMethod
  fun setFontName(fontName: String, promise: Promise) {
    validatePrinterService(promise)
    try {
      val _fontName = when (fontName) {
        "chineseMonospaced" -> "gh"
        else -> null
      }
      if (_fontName != null){
        val callback = makeInnerResultCallback(promise, "native#setFontName() is failed.")
        printerService?.setFontName(_fontName, callback)
      } else {
        promise.reject("0", "native#setFontName() is failed because key is incorrect.")
      }
    } catch (e: Exception) {
      promise.reject("0", "native#setFontName() is failed. " + e.message)
    }
  }

  @ReactMethod
  fun setFontSize(fontSize: Float, promise: Promise) {
    validatePrinterService(promise)
    try {
      val callback = makeInnerResultCallback(promise, "native#setFontSize() is failed.")
      printerService?.setFontSize(fontSize, callback)
    } catch (e: Exception) {
      promise.reject("0", "native#setFontSize() is failed. " + e.message)
    }
  }

  @ReactMethod
  fun setBold(isBold: Boolean, promise: Promise) {
    validatePrinterService(promise)
    try {
      val callback = makeInnerResultCallback(promise, "native#setBold() is failed.")
      val data = ByteArray(3)
      data[0] = 0x1B
      data[1] = 0x45
      data[2] = if (isBold) { 0x1 } else { 0x0 }
      printerService?.sendRAWData(data, callback);
    } catch (e: Exception) {
      promise.reject("0", "native#setBold() is failed. " + e.message)
    }
  }

  @ReactMethod
  fun printText(text: String, promise: Promise) {
    validatePrinterService(promise)
    try {
      val callback = makeInnerResultCallback(promise, "native#printText() is failed.")
      printerService?.printText(text + "\n", callback)
    } catch (e: Exception) {
      promise.reject("0", "native#printText() is failed. " + e.message)
    }
  }

  @ReactMethod
  fun printTextWithFont(text: String, typeface: String, fontSize: Float, promise: Promise) {
    validatePrinterService(promise)
    try {
      val callback = makeInnerResultCallback(promise, "native#printTextWithFont is failed.")
      val _typeface = when (typeface) {
        "default" -> ""
        else -> null
      }
      if (_typeface != null){
        printerService?.printTextWithFont(text + "\n", _typeface, fontSize, callback)
      } else {
        promise.reject("0", "native#printTextWithFont is failed because typeface is incorrect.")
      }
    } catch (e: Exception) {
      promise.reject("0", "native#printTextWithFont is failed. " + e.message)
    }
  }

  @ReactMethod
  fun printOriginalText(text: String, promise: Promise) {
    validatePrinterService(promise)
    try {
      val callback = makeInnerResultCallback(promise, "native#printOriginalText is failed.")
      printerService?.printOriginalText(text + "\n", callback)
    } catch (e: Exception) {
      promise.reject("0", "native#printOriginalText is failed. " + e.message)
    }
  }

  @ReactMethod
  fun printColumnsText(texts: ReadableArray, widths: ReadableArray, alignments: ReadableArray, promise: Promise) {
    validatePrinterService(promise)
    try {
      val callback = makeInnerResultCallback(promise, "native#printColumnsText is failed.")

      var _texts = arrayOf<String>()
      for (i in 0..(texts.size()-1)){
        _texts += texts.getString(i)
      }

      var _widths = intArrayOf()
      for (i in 0..(widths.size()-1)){
        _widths += widths.getInt(i)
      }

      var _alignments = intArrayOf()
      for (i in 0..(alignments.size()-1)){
        val temp = alignmentToInt(alignments.getString(i))
        if(temp != null){
          _alignments += temp
        }
      }

      if (_texts.size == _alignments.size && _texts.size == _widths.size) {
         printerService?.printColumnsText(_texts, _widths, _alignments, callback)
       } else {
         promise.reject("0", "native#printColumnsText is failed because alignments is incorrect.")
       }
    } catch (e: Exception) {
      promise.reject("0", "native#printColumnsText is failed. " + e.message)
    }
  }

  @ReactMethod
  fun printColumnsString(texts: ReadableArray, widths: ReadableArray, alignments: ReadableArray, promise: Promise) {
    validatePrinterService(promise)
    try {
      val callback = makeInnerResultCallback(promise, "native#printColumnsString is failed.")

      var _texts = arrayOf<String>()
      for (i in 0..(texts.size()-1)){
        _texts += texts.getString(i)
      }

      var _widths = intArrayOf()
      for (i in 0..(widths.size()-1)){
        _widths += widths.getInt(i)
      }

      var _alignments = intArrayOf()
      for (i in 0..(alignments.size()-1)){
        val temp = alignmentToInt(alignments.getString(i))
        if(temp != null){
          _alignments += temp
        }
      }

      if (_texts.size == _alignments.size && _texts.size == _widths.size) {
         printerService?.printColumnsString(_texts, _widths, _alignments, callback)
       } else {
         promise.reject("0", "native#printColumnsString is failed because alignments is incorrect.")
       }
    } catch (e: Exception) {
      promise.reject("0", "native#printColumnsString is failed. " + e.message)
    }
  }

  @ReactMethod
  fun printBarcode(text: String, symbology: String, height: Int, width: Int, textPosition: String, promise: Promise) {
    validatePrinterService(promise)
    try {
      val _symbology = when (symbology) {
        "UPC-A" -> 0
        "UPC-E" -> 1
        "JAN13(EAN13)" -> 2
        "JAN8(EAN8)" -> 3
        "CODE39" -> 4
        "ITF" -> 5
        "CODABAR" -> 6
        "CODE93" -> 7
        "CODE128" -> 8
        else -> null
      }
      val _textPosition = when (textPosition) {
        "none" -> 0
        "textAboveBarcode" -> 1
        "textUnderBarcode" -> 2
        "textAboveAndUnderBarcode" -> 3
        else -> null
      }

      if (_symbology != null && _textPosition != null) {
        val callback = makeInnerResultCallback(promise, "native#printBarCode is failed.")
        printerService?.printBarCode(text, _symbology, height, width, _textPosition, callback)
      } else {
        promise.reject("0", "native#printBarCode is failed because alignments is incorrect.")
      }
    } catch (e: Exception) {
      promise.reject("0", "native#printBarCode is failed. " + e.message)
    }
  }

  @ReactMethod
  fun printQRCode(text: String, moduleSize: Int, errorLevel: String, promise: Promise) {
    validatePrinterService(promise)
    try {
      val _errorLevel = when (errorLevel) {
        "low" -> 0
        "middle" -> 1
        "quartile" -> 2
        "high" -> 3
        else -> null
      }
      if (_errorLevel != null) {
        val callback = makeInnerResultCallback(promise, "native#printQRCode is failed.")
        printerService?.printQRCode(text, moduleSize, _errorLevel, callback)
      } else {
        promise.reject("0", "native#printQRCode is failed because alignments is incorrect.")
      }
    } catch (e: Exception) {
      promise.reject("0", "native#printQRCode is failed. " + e.message)
    }
  }

  @ReactMethod
  fun print2DCode(text: String, symbology: Int, moduleSize: Int, errorLevel: Int, promise: Promise) {
    validatePrinterService(promise)
    try {
      val callback = makeInnerResultCallback(promise, "native#print2DCode is failed.")
        printerService?.print2DCode(text, symbology, moduleSize, errorLevel, callback)
    } catch (e: Exception) {
      promise.reject("0", "native#print2DCode is failed. " + e.message)
    }
  }

  @ReactMethod
  fun lineWrap(count: Int, promise: Promise) {
    validatePrinterService(promise)
    try {
      val callback = makeInnerResultCallback(promise, "native#lineWrap is failed.")
      printerService?.lineWrap(count, callback)
    } catch (e: Exception) {
      promise.reject("0", "native#lineWrap is failed. " + e.message)
    }
  }

  @ReactMethod
  fun cutPaper(promise: Promise) {
    validatePrinterService(promise)
    try {
      val callback = makeInnerResultCallback(promise, "native#cutPaper is failed.")
      printerService?.cutPaper(callback)
    } catch (e: Exception) {
      promise.reject("0", "native#cutPaper is failed. " + e.message)
    }
  }

  @ReactMethod
  fun getCutPaperTimes(promise: Promise) {
    validatePrinterService(promise)
    try {
      val result = printerService?.getCutPaperTimes()
      promise.resolve(result)
    } catch (e: Exception) {
      promise.reject("0", "native#cutPaper is failed. " + e.message)
    }
  }

  @ReactMethod
  fun printBitmapBase64(base64: String, pixelWidth: Int, promise: Promise) {
    validatePrinterService(promise)
    try {
      val callback = makeInnerResultCallback(promise, "native#printBitmapBase64 is failed.")
      val pureBase64Encoded = base64.substring(base64.indexOf(",") + 1)
      val decodedBytes = Base64.decode(pureBase64Encoded, Base64.DEFAULT)
      val decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
      val w = decodedBitmap.width
      val h = decodedBitmap.height
      val image = Bitmap.createScaledBitmap(decodedBitmap, pixelWidth, pixelWidth / w * h, false)
      printerService?.printBitmap(image, callback)
    } catch (e: Exception) {
      promise.reject("0", "native#printBitmapBase64 is failed. " + e.message)
    }
  }

  @ReactMethod
  fun printBitmapBase64Custom(base64: String, pixelWidth: Int, type: Int, promise: Promise) {
    validatePrinterService(promise)
    try {
      val callback = makeInnerResultCallback(promise, "native#printBitmapBase64Custom is failed.")
      val pureBase64Encoded = base64.substring(base64.indexOf(",") + 1)
      val decodedBytes = Base64.decode(pureBase64Encoded, Base64.DEFAULT)
      val decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
      val w = decodedBitmap.width
      val h = decodedBitmap.height
      val image = Bitmap.createScaledBitmap(decodedBitmap, pixelWidth, pixelWidth / w * h, false)
      printerService?.printBitmapCustom(image, type, callback)
    } catch (e: Exception) {
      promise.reject("0", "native#printBitmapBase64Custom is failed. " + e.message)
    }
  }

  /**
   * printerService が有効かどうか調べる
   */
  private fun validatePrinterService(promise: Promise) {
    if (printerService == null){
      promise.reject("0", "PrinterService is disabled because InnerPrinter is not connected.")
    }
  }

  /**
   * 結果用のコールバックを生成する
   */
  private fun makeInnerResultCallback(promise: Promise, errorMessage: String? = null): InnerResultCallback {
    val callback: InnerResultCallback = object : InnerResultCallback() {
      override fun onRunResult(isSuccess: Boolean) {
        if (isSuccess){
          promise.resolve(isSuccess)
        } else {
          val message = if (errorMessage != null) errorMessage else "onRunResult is failed."
          promise.reject("0", message)
        }
      }
      override fun onReturnString(result: String) {
        promise.resolve(result)
      }
      override fun onRaiseException(code: Int, msg: String) {
        promise.reject(code.toString(), msg)
      }
      override fun onPrintResult(code: Int, msg: String) {
        promise.resolve(code.toString() + " " + msg)
      }
    }
    return callback
  }

  private fun alignmentToInt(alignment: String): Int? {
    val value = when (alignment) {
      "left" -> 0
      "center" -> 1
      "right" -> 2
      else -> null
    }
    return value
  }

  @ReactMethod
  fun enterPrinterBuffer(clear: Boolean, promise: Promise) {
    validatePrinterService(promise)
    try {
      printerService?.enterPrinterBuffer(clear)
      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("0", "native#enterPrinterBuffer is failed. " + e.message)
    }
  }

  @ReactMethod
  fun exitPrinterBuffer(commit: Boolean, promise: Promise) {
    validatePrinterService(promise)
    try {
      val callback = makeInnerResultCallback(promise, "native#exitPrinterBuffer() is failed.")
      printerService?.exitPrinterBufferWithCallback(commit, callback)
    } catch (e: Exception) {
      promise.reject("0", "native#exitPrinterBuffer is failed. " + e.message)
    }
  }

  @ReactMethod
  fun commitPrinterBuffer(promise: Promise) {
    validatePrinterService(promise)
    try {
      val callback = makeInnerResultCallback(promise, "native#commitPrinterBuffer() is failed.")
      printerService?.commitPrinterBufferWithCallback(callback)
    } catch (e: Exception) {
      promise.reject("0", "native#commitPrinterBuffer is failed. " + e.message)
    }
  }
}
