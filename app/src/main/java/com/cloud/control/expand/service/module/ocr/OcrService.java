package com.cloud.control.expand.service.module.ocr;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.cloud.control.expand.service.R;
import com.cloud.control.expand.service.aidl.TargetImg;
import com.cloud.control.expand.service.entity.ExpandService;
import com.cloud.control.expand.service.entity.ExpandServiceRecordEntity;
import com.cloud.control.expand.service.home.ExpandServiceApplication;
import com.cloud.control.expand.service.log.KLog;
import com.cloud.control.expand.service.retrofit.manager.RetrofitServiceManager;
import com.cloud.control.expand.service.utils.ConstantsUtils;
import com.cloud.control.expand.service.utils.DateUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import rx.Subscriber;
import rx.functions.Action0;


/**
 * @author wangyou
 * @desc:
 * @date :2021/2/3
 */
public class OcrService extends Service {
    private static final String TAG = "OcrService";

    // Model settings of object detection
//    private boolean updateParams = false;
//    protected String modelPath = "";
//    protected String labelPath = "";
//    protected String imagePath = "";
//    protected int cpuThreadNum = 1;
//    protected String cpuPowerMode = "";
//    protected String inputColorFormat = "";
//    protected long[] inputShape = new long[]{};
//    protected float[] inputMean = new float[]{};
//    protected float[] inputStd = new float[]{};
//    protected float scoreThreshold = 0.1f;
//    private String currentPhotoPath;

    protected Predictor predictor = new Predictor();
    protected OcrParams ocrParams = null;
    protected TargetImg targetImg = null;
    protected float confidence = 0.1f;
    protected List<Disposable> mDisposables = new ArrayList<>();
    private OnResultListener onResultListener = null;
    private InitModelListener initModelListener = null;

    /**????????????????????????*/
    private boolean isDeadline = false;
    /**??????????????????????????????*/
    private boolean customSetting = false;
    /**??????????????????*/
    private boolean isRecognizing = false;

    public OcrService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private IOCRService.Stub binder = new IOCRService.Stub() {
        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            /*???????????????????????????????????????*/
            int check = checkCallingPermission("com.cloud.control.expand.service.aidl.permission.OCR_SERVICE");
            if (check == PackageManager.PERMISSION_DENIED){
                return false;
            }
//            String packageName = null;
//            String[] packages = getPackageManager().getPackagesForUid(getCallingUid());
//            if(packages != null && packages.length > 0){
//                packageName = packages[0];
//            }else {
//                return false;
//            }
//            if(packageName != null && !packageName.startsWith("com.muse.ocrdemo")){
//                return false;
//            }
            return super.onTransact(code, data, reply, flags);
        }

        @Override
        public void initModel(InitModelListener initModelListener) throws RemoteException{
            setInitModelListener(initModelListener);
            //???????????????
            Log.d(TAG,"?????????????????????");
            loadModel(getDefOcrParams());
        }
        @Override
        public void inputImg(TargetImg targetImg, OnResultListener onResultListener) throws RemoteException {
            setOnResultListener(onResultListener);
            setImage(targetImg);
            runModelPrelude();
        }

        @Override
        public void advancedSetup(float confidence, int cpuThreadNum, String cpuPowerMode) throws RemoteException {
            if (isRecognizing && onResultListener != null){
                onResultListener.onFailed("???????????????????????????????????????");
                return;
            }
            if (targetImg == null && onResultListener != null){
                Log.e(TAG, "????????????????????????????????????");
                onResultListener.onFailed("????????????????????????????????????");
                return;
            }else if (onResultListener == null){
                Log.e(TAG, "OnResultListener is null!");
                return;
            }
            customSetting = true;
            ocrParams.setScoreThreshold(confidence);
            ocrParams.setCpuThreadNum(cpuThreadNum);
            ocrParams.setCpuPowerMode(cpuPowerMode);
            loadModel(ocrParams);
        }
    };

    public void setImage(TargetImg targetImg){
        this.targetImg = targetImg;
    }

    private void setConfidence(float confidence){
        if (this.confidence != confidence){
            this.confidence = confidence;
            this.customSetting = true;
            ocrParams.setScoreThreshold(confidence);
        }
    }

    private void setOnResultListener(OnResultListener onResultListener) {
        this.onResultListener = onResultListener;
    }

    public void setInitModelListener(InitModelListener initModelListener) {
        this.initModelListener = initModelListener;
    }

    /**
     * ??????????????????????????????
     */
    public void checkDeadline(){
        if (onResultListener == null) {
            Log.e(TAG, "onResultListener is null");
            return;
        }
        RetrofitServiceManager.getExtendServiceRecord()
                .subscribe(new Subscriber<ExpandServiceRecordEntity>() {
                    @Override
                    public void onCompleted() {
                        KLog.e("getExtendServiceRecord onCompleted");
                    }

                    @Override
                    public void onError(Throwable e) {
                        KLog.e("getExtendServiceRecord onError:"+e.getMessage());
                        isDeadline = true;
                        try {
                            onResultListener.onFailed("??????????????????"+e.getMessage());
                        } catch (RemoteException re) {
                            re.printStackTrace();
                        }
                    }

                    @Override
                    public void onNext(ExpandServiceRecordEntity recordEntity) {
                        KLog.d("getExtendServiceRecord onNext " + recordEntity.toString());
                        if (recordEntity.getData() != null && recordEntity.getData().size() > 0) {
                            List<Integer> typeIds = new ArrayList<>();
                            for (ExpandServiceRecordEntity.DataBean dataBean : recordEntity.getData()){
                                typeIds.add(dataBean.getTypeId());
                                if (dataBean.getTypeId() == ExpandService.OCR.getTypeId()){
                                    if(DateUtils.isExpire(dataBean.getCurrentTime(), dataBean.getDueTimeStr())){
                                        Log.e(TAG, "???????????????");
                                        deadLine();
                                    }else {
                                        Log.d(TAG, "????????????");
                                        isDeadline = false;
                                        //????????????????????????
                                        runModel();
//                                        if (ocrParams != null) {
//                                            loadModel(ocrParams);
//                                        }else {
//                                            try {
//                                                throw new RemoteException("ocrParams is null!");
//                                            } catch (RemoteException e) {
//                                                e.printStackTrace();
//                                            }
//                                        }
                                    }
                                    break;
                                }
                            }
                            if (!typeIds.contains(ExpandService.OCR.getTypeId())){
                                deadLine();
                            }
                        }else{
                            deadLine();
                        }
                    }
                });
    }

    public void deadLine(){
        isDeadline = true;
        try {
            onResultListener.onFailed(ExpandServiceApplication.getInstance().getString(R.string.expand_service_deadline));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * ????????????
     *
     * @param ocrParams
     */
    public synchronized void loadModel(OcrParams ocrParams) {
        this.ocrParams = ocrParams;
        Disposable disposable = Single.create(new SingleOnSubscribe<Boolean>() {
            @Override
            public void subscribe(SingleEmitter<Boolean> emitter) throws Exception {
                emitter.onSuccess(onLoadModel());
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean onLoadModel) throws Exception {
                        Log.d(TAG, "?????????????????????" + onLoadModel);
                        if (onLoadModel) {
                            onLoadModelSuccessed();
                        } else {
                            onLoadModelFailed();
                        }
                    }
                });
        mDisposables.add(disposable);
    }

    /**
     * ??????????????????
     */
    private synchronized void runModelPrelude(){
        if (isRecognizing && onResultListener != null){
            try {
                onResultListener.onFailed("????????????????????????????????????");
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            return;
        }
        checkDeadline();
    }
    /**
     * ?????????????????????????????????
     */
    private void runModel() {
        isRecognizing = true;
        Log.d(TAG, "??????????????????");
        if (targetImg != null && predictor.isLoaded()) {
            predictor.setInputImage(targetImg.getTarget());
        }else {
            return;
        }
        Disposable disposable = Single.create(new SingleOnSubscribe<Boolean>() {
            @Override
            public void subscribe(SingleEmitter<Boolean> emitter) throws Exception {
                Log.d(TAG, "?????????onRunModel??????");
                emitter.onSuccess(onRunModel());
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean onRunModel) throws Exception {
                        Log.d(TAG, "?????????????????????" + onRunModel);
                        isRecognizing = false;
                        if (onRunModel) {
                            onRunModelSuccess();
                        }else {
                            onRunModelFailed();
                        }
                    }
                });
        mDisposables.add(disposable);
    }

    public boolean onLoadModel() {
        if (ocrParams == null) {
            return false;
        }
        return predictor.init(this,
                ocrParams.getModelPath(),
                ocrParams.getLabelPath(),
                ocrParams.getCpuThreadNum(),
                ocrParams.getCpuPowerMode(),
                ocrParams.getInputColorFormat(),
                ocrParams.getInputShape(),
                ocrParams.getInputMean(),
                ocrParams.getInputStd(),
                ocrParams.getScoreThreshold());
    }

    public boolean onRunModel() {
        return predictor.isLoaded() && predictor.runModel();
    }

    public void onLoadModelSuccessed() {
        // Load test image from path and run model
        Log.d(TAG, "??????????????????");
        if (initModelListener != null){
            try {
                initModelListener.onLoadSuccess();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        /*??????????????????????????????????????????*/
        if (customSetting){
            customSetting = false;
            runModelPrelude();
        }
    }

    public void onLoadModelFailed() {
        if (initModelListener != null){
            try {
                initModelListener.onLoadFailed("??????????????????");
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void onRunModelSuccess() throws RemoteException {
        Log.d(TAG, "Inference time: " + predictor.inferenceTime() + " ms");
        if (onResultListener != null) {
            ArrayList<OcrResultModel> results = predictor.getOcrResult();
            JsonArray jsonArray = new JsonArray();
            if (results != null) {
                for (int i = 0; i < results.size(); i++) {
                    OcrResultModel result = results.get(i);
                    StringBuilder sb = new StringBuilder("");
                    sb.append(result.getLabel());
                    sb.append(" ").append(result.getConfidence());
                    sb.append("; Points: ");
                    for (Point p : result.getPoints()) {
                        sb.append("(").append(p.x).append(",").append(p.y).append(") ");
                    }
                    Log.i(TAG, sb.toString());
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("id", String.valueOf(i + 1));
                    jsonObject.addProperty("word", result.getLabel());
                    jsonArray.add(jsonObject);
                }
            }
            onResultListener.onSuccess(new Gson().toJson(jsonArray), predictor.inferenceTime);
        }
    }

    public void onRunModelFailed() throws RemoteException {
        Log.e(TAG, "???????????????????????????????????????");
        if (onResultListener != null){
            onResultListener.onFailed("???????????????????????????????????????");
        }
    }

    public OcrParams getDefOcrParams() {
        OcrParams ocrParams = new OcrParams();
        ocrParams.setModelPath(ConstantsUtils.OcrParams.MODEL_PATH_DEFAULT);
        ocrParams.setLabelPath(ConstantsUtils.OcrParams.LABEL_PATH_DEFAULT);
        ocrParams.setImagePath(ConstantsUtils.OcrParams.IMAGE_PATH_DEFAULT);
        ocrParams.setCpuThreadNum(ConstantsUtils.OcrParams.CPU_THREAD_NUM_DEFAULT);
        ocrParams.setCpuPowerMode(ConstantsUtils.OcrParams.CPU_POWER_MODE_DEFAULT);
        ocrParams.setInputColorFormat(ConstantsUtils.OcrParams.INPUT_COLOR_FORMAT_DEFAULT);
        ocrParams.setInputShape(Utils.parseLongsFromString(ConstantsUtils.OcrParams.INPUT_SHAPE_DEFAULT, ","));
        ocrParams.setInputMean(Utils.parseFloatsFromString(ConstantsUtils.OcrParams.INPUT_MEAN_DEFAULT, ","));
        ocrParams.setInputStd(Utils.parseFloatsFromString(ConstantsUtils.OcrParams.INPUT_STD_DEFAULT, ","));
        return ocrParams;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mDisposables != null) {
            for (Disposable d : mDisposables) {
                if (d != null && !d.isDisposed()) {
                    d.dispose();
                }
            }
        }
    }
}
