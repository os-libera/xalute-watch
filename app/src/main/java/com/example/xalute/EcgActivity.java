
/*
 * Copyright 2022 Korea University(os.korea.ac.kr). All rights reserved.
 *
 * EcgActivity.java - Xalute (Digital Health Platform Project)
 *
 */


package com.example.xalute;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.FragmentActivity;

import com.samsung.android.service.health.tracking.ConnectionListener;
import com.samsung.android.service.health.tracking.HealthTracker;
import com.samsung.android.service.health.tracking.HealthTrackerException;
import com.samsung.android.service.health.tracking.HealthTrackingService;
import com.samsung.android.service.health.tracking.data.DataPoint;
import com.samsung.android.service.health.tracking.data.HealthTrackerType;
import com.samsung.android.service.health.tracking.data.ValueKey;

import com.example.xalute.R;

import android.os.CountDownTimer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import java.util.Date;
import java.text.SimpleDateFormat;

// test
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import android.app.AlertDialog;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

import org.json.JSONObject;
import org.json.JSONException;
import android.view.LayoutInflater;

import com.google.android.gms.wearable.Asset;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.BufferedReader;
import java.io.FileReader;

import com.example.xalute.databinding.ActivityEcgBinding;
public class EcgActivity extends FragmentActivity {

    private final String TAG = EcgActivity.class.getSimpleName();
    private ActivityEcgBinding binding;
    private HealthTrackingService healthTrackingService = null;
    @NotNull
    private final String[] permissions = {"android.permission.BODY_SENSORS"};
    private final int REQUEST_ACCOUNT_PERMISSION = 100;
    private boolean isHandlerRunning;
    private final Handler handler = new Handler(Looper.myLooper());
    private HealthTracker ecgTracker = null;

    private boolean isTimerRunning = false;
    private boolean isFirst;

    private int ecgContactState;
    public static final int ECG_NOT_CONTACTED = 0;
    public static final int ECG_CONTACTED = 1;

    private List<EcgData> ecgDataList = new ArrayList<>();
    CountDownTimer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        String name = prefs.getString("name", "");
        String birthDate = prefs.getString("birthDate", "");
        Log.d("확인용", "EcgActivity에서 읽은 이름: " + name + ", 생일: " + birthDate);

        ecgContactState = ECG_NOT_CONTACTED;

        if (PermissionActivity.checkPermission(this, this.permissions)) {
            Log.i(TAG, "onCreate Permission granted");
            setUp();

            binding.btnSend.setOnClickListener(view -> {
                Log.d(TAG, "[Send] 버튼 클릭됨. ecgDataList 크기=" + ecgDataList.size());

                if (ecgDataList.isEmpty()) {
                    Toast.makeText(getApplicationContext(), "❗ ECG 데이터가 없습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }

                String server1Url = "http://34.121.231.99:3000/mutation/addData";
                String currentTime = getCurrentTime();
                String fhirData = EcgDataConverter.convertEcgDataListToJsonString(getApplicationContext(), ecgDataList, currentTime);
                Log.d(TAG, "[Send] 저장할 JSON: " + fhirData);

                // ✅ 로딩 UI 먼저 표시
                showProgressDialog();

                // ✅ UI 먼저 렌더링 → 그 후 전송 시작
                new Handler(Looper.getMainLooper()).post(() -> {
                    FHIRSender sender = new FHIRSender(getApplicationContext(), new FHIRSender.FHIRSenderListener() {
                        @Override
                        public void onSendCompleted(boolean success) {
                            runOnUiThread(() -> {
                                if (success) {
                                    Log.d(TAG, "✅ 저장 성공");
                                    File ecgFile = saveEcgDataToFile();
                                    if (ecgFile != null) {
                                        uploadEcgFileToServer(ecgFile);
                                    } else {
                                        dismissProgressDialog();
                                        Toast.makeText(getApplicationContext(), "❌ ECG 파일 저장 실패", Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    dismissProgressDialog();
                                    Log.e(TAG, "❌ 저장 실패");
                                    Toast.makeText(getApplicationContext(), "❌ FHIR 데이터 전송 실패", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });

                    sender.execute(fhirData, server1Url);
                });
            });

        } else {
            Log.i(TAG, "onCreate Permission not granted");
            PermissionActivity.showPermissionPrompt(this, this.REQUEST_ACCOUNT_PERMISSION, this.permissions);
        }
    }


    private void StartCountTimer() {
        // 타이머 객체 정의
        if ( ecgTracker != null ) {

            timer = new CountDownTimer(30000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    Log.i(TAG, " StartCountTimer: onTick called - "+ millisUntilFinished/1000);
                    if ( ecgContactState == ECG_CONTACTED )
                        //binding.ecgSecond.setText(String.valueOf(count++));
                        binding.ecgSecond.setText("남은 시간: " + millisUntilFinished/1000);
                    else {
                        binding.ecgSecond.setText(String.valueOf(30));
                        if ( ecgContactState == ECG_CONTACTED ) {
                            StopCountTimer();
                            StopTrackerListner();
                        }
                        //Log.i(TAG, " CountDownTimer : " +  count);
                    }
                }

                @Override
                public void onFinish() {
                    Log.d("ECGActivity", "onFinish called");

                    OnMessage("ECG Measurement Ended...!!");
                    Toast.makeText(getApplicationContext(), "측정완료! SEND버튼을 눌러 전송하세요!", Toast.LENGTH_LONG).show();
                    StopTrackerListner();

                }
            }.start();

            isTimerRunning = true;
        }
    }

    private void StopCountTimer() {
        timer.cancel();
        timer = null;
        isTimerRunning = false;
    }

    private void ECGMeasurementError() {
        Log.i(TAG, " ECGMeasurementError ");

        if ( ecgContactState == ECG_CONTACTED ) {
            StopCountTimer();
            StopTrackerListner();
            Intent intent = new Intent(getApplicationContext(), EcgInfoActivity.class);
            startActivity(intent);
            this.finish();
        }
    }

    private void StartTrackerListner() {
        Log.i(TAG, " StartListner: setEventListener called ");
        if(!isHandlerRunning) {
            handler.post(() -> {
                ecgTracker.setEventListener(trackerEventListener);
                isHandlerRunning = true;
            });
        }
    }

    private void StopTrackerListner() {
        Log.i(TAG, " StopListner: unsetEventListener called ");
        if (ecgTracker != null) {
            ecgTracker.unsetEventListener();
        }
        handler.removeCallbacksAndMessages(null);
        isHandlerRunning = false;

        if(healthTrackingService != null) {
            healthTrackingService.disconnectService();
        }
    }


    private final ConnectionListener connectionListener = new ConnectionListener() {
        @Override
        public void onConnectionSuccess() {
            Toast.makeText(
                    getApplicationContext(),"Connected to HSP",Toast.LENGTH_SHORT
            ).show();

            try {
                ecgTracker = healthTrackingService.getHealthTracker(HealthTrackerType.ECG);
            } catch (final IllegalArgumentException e) {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show()
                );
                finish();
            }

            StartTrackerListner();
            //StartCountTimer();
        }

        @Override
        public void onConnectionEnded() {

        }

        @Override
        public void onConnectionFailed(HealthTrackerException e) {
            if(e.hasResolution()) {
                e.resolve(EcgActivity.this);
            }
            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Unable to connect to HSP", Toast.LENGTH_LONG).show()
            );
            finish();
        }
    };

    public void addEcgData(int ecgValue, long timestamp) {
        EcgData newEcgData = new EcgData(ecgValue, timestamp);
        ecgDataList.add(newEcgData);
        Log.d(TAG, "✅ 저장된 ECG 데이터 개수: " + ecgDataList.size());
    }
    private final HealthTracker.TrackerEventListener trackerEventListener = new HealthTracker.TrackerEventListener() {
        @Override
        public void onDataReceived(@NonNull List<DataPoint> list) {
            if (!list.isEmpty()) {
                Log.i(TAG, "✅ List Size : " + list.size());

                for (DataPoint dataPoint : list) {
                    long timestamp = dataPoint.getTimestamp();
                    int ecgVal = dataPoint.getValue(ValueKey.EcgSet.ECG);
                    int leadOff = dataPoint.getValue(ValueKey.EcgSet.LEAD_OFF);

                    Log.i(TAG, "Timestamp : " + timestamp);
                    Log.i(TAG, "ECG value : " + ecgVal);

                    // ✅ 수정: leadOff 관계없이 모두 저장
                    addEcgData(ecgVal, timestamp);
                }

                runOnUiThread(() -> {
                    int leadOff = list.get(0).getValue(ValueKey.EcgSet.LEAD_OFF);
                    int sampleEcg = list.get(0).getValue(ValueKey.EcgSet.ECG);

                    if (leadOff == 0) {
                        ecgContactState = ECG_CONTACTED;
                        if (!isTimerRunning) StartCountTimer();

                        binding.ecgAverage.setText(String.valueOf(sampleEcg));
                        binding.ecg1DataValue.setText(String.valueOf(sampleEcg));
                        binding.leadOffDataValue.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.green));
                    } else {
                        binding.leadOffDataValue.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.red));
                        ECGMeasurementError();
                        ecgContactState = ECG_NOT_CONTACTED;
                        Toast.makeText(getApplicationContext(), "위 빨간 홈 버튼에 손가락을 가볍게 올려 놓아주세요.", Toast.LENGTH_SHORT).show();
                    }

                    binding.leadOffDataValue.setText(String.valueOf(leadOff));
                    binding.sequenceValue.setText(String.valueOf(list.get(0).getValue(ValueKey.EcgSet.SEQUENCE)));

                    if (list.size() >= 6) {
                        int avgGreen = (list.get(0).getValue(ValueKey.EcgSet.PPG_GREEN) + list.get(5).getValue(ValueKey.EcgSet.PPG_GREEN)) / 2;
                        binding.ecgGreenDataValue.setText(String.valueOf(avgGreen));
                    } else {
                        binding.ecgGreenDataValue.setText(String.valueOf(list.get(0).getValue(ValueKey.EcgSet.PPG_GREEN)));
                    }

                    binding.thresholdMaxDataValue.setText(String.valueOf(list.get(0).getValue(ValueKey.EcgSet.MAX_THRESHOLD)));
                    binding.thresholdMinDataValue.setText(String.valueOf(list.get(0).getValue(ValueKey.EcgSet.MIN_THRESHOLD)));
                });

            } else {
                Log.i(TAG, "⚠️ onDataReceived List is zero");
            }
        }

        @Override
        public void onFlushCompleted() {
            Log.i(TAG, "✅ onFlushCompleted called");
        }

        @Override
        public void onError(HealthTracker.TrackerError trackerError) {
            Log.i(TAG, "❌ onError called");

            runOnUiThread(() -> {
                if (trackerError == HealthTracker.TrackerError.PERMISSION_ERROR) {
                    Toast.makeText(getApplicationContext(), "Permissions Check Failed", Toast.LENGTH_SHORT).show();
                } else if (trackerError == HealthTracker.TrackerError.SDK_POLICY_ERROR) {
                    Toast.makeText(getApplicationContext(), "SDK Policy denied", Toast.LENGTH_SHORT).show();
                }
            });

            isHandlerRunning = false;
        }
    };

    public final void setUp() {
        Log.i(TAG, "setUp");
        binding = DataBindingUtil.setContentView(this, R.layout.activity_ecg);
        healthTrackingService = new HealthTrackingService(connectionListener, getApplicationContext());
        healthTrackingService.connectService();
    }


    private void OnMessage(String message) {
        Context context = getApplicationContext();
        CharSequence text = message;
        int duration = Toast.LENGTH_SHORT;

        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i(TAG, "onActivityResult requestCode = " + requestCode + " resultCode = " + resultCode);
        if (requestCode == this.REQUEST_ACCOUNT_PERMISSION) {
            if (resultCode == -1) {
                setUp();
            } else {
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(ecgTracker != null) {
            ecgTracker.unsetEventListener();
            ecgTracker = null;
        }

        handler.removeCallbacksAndMessages(null);
        isHandlerRunning = false;

        if(healthTrackingService != null) {
            healthTrackingService.disconnectService();
            healthTrackingService = null;
        }
    }

    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        return sdf.format(new Date());
    }

    interface ApiService {
        @Multipart
        @POST("/predict_single_lead") // 서버의 엔드포인트
        Call<ResponseBody> uploadEcgFile(@Part MultipartBody.Part file);
    }

    private void uploadEcgFileToServer(File file) {
        if (file == null || !file.exists()) {
            Log.e(TAG, "파일이 존재하지 않습니다.");
            Toast.makeText(this, "파일이 존재하지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "📤 전송할 ECG 파일 경로: " + file.getAbsolutePath());

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder fileContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                fileContent.append(line).append("\n");
            }
            Log.d(TAG, "📤 전송할 ECG 파일 내용:\n" + fileContent.toString().trim());
        } catch (IOException e) {
            Log.e(TAG, "❌ ECG 파일 내용 읽기 오류", e);
        }

        // Retrofit 인스턴스
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://34.69.44.173:7000")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ApiService apiService = retrofit.create(ApiService.class);

        // 요청 본문
        RequestBody requestBody = RequestBody.create(MediaType.parse("text/plain"), file);
        MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", file.getName(), requestBody);

        // 서버로 업로드
        Call<ResponseBody> call = apiService.uploadEcgFile(filePart);
        Log.d(TAG, ">>>> 업로드 요청을 보낼 URL: " + call.request().url().toString());
        Log.d(TAG, ">>>> Retrofit Base URL: " + retrofit.baseUrl());
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                dismissProgressDialog();
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String resultJson = response.body().string();
                        Log.d(TAG, "서버 응답: " + resultJson);

                        // ✅ 진단 결과 판별
                        JSONObject jsonResponse = new JSONObject(resultJson);
                        String resultToSend = jsonResponse.has("result") &&
                                jsonResponse.get("result") instanceof JSONObject &&
                                ((JSONObject) jsonResponse.get("result")).has("distance_from_median")
                                ? "abnormal"
                                : "normal";

                        showServerResponse(resultToSend); // 기존 AlertDialog

                        // ✅ 수정된 전송 함수 호출
                        sendFileToPhone(file, resultToSend, resultJson);

                    } catch (Exception e) {
                        Log.e(TAG, "응답 처리 중 오류 발생", e);
                    }
                }
                else {
                    Log.e(TAG, "서버 오류: " + response.code());
                    if (response.errorBody() != null) {
                        try {
                            String error = response.errorBody().string();
                            Log.e(TAG, "❌ 서버 응답 내용(errorBody): " + error);
                        } catch (IOException e) {
                            Log.e(TAG, "❌ 서버 오류 본문 읽기 실패", e);
                        }
                    } else {
                        Log.e(TAG, "⚠️ 서버 errorBody가 null입니다.");
                    }

                    Toast.makeText(EcgActivity.this, "서버 오류: " + response.code(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                dismissProgressDialog();
                Log.e(TAG, "네트워크 오류 발생", t);
                Toast.makeText(EcgActivity.this, "네트워크 오류 발생", Toast.LENGTH_LONG).show();
            }

        });
    }

    private File saveEcgDataToFile() {
        File dir = new File(getExternalFilesDir(null), "ecg_data");
        if (!dir.exists()) {
            dir.mkdirs(); // 폴더가 없으면 생성
        }

        String fileName = "ecg_" + System.currentTimeMillis() + ".txt";
        File file = new File(dir, fileName);

        try (FileWriter writer = new FileWriter(file)) {
            StringBuilder sb = new StringBuilder();

            double intervalSec = 1.0 / 512.0;  // fs = 512Hz
            int originalSize = ecgDataList.size();

            // 전체 3번 반복 저장
            for (int repeat = 0; repeat < 1; repeat++) {
                for (int i = 0; i < originalSize; i++) {
                    EcgData data = ecgDataList.get(i);
                    int globalIndex = repeat * originalSize + i;
                    double syntheticTimestamp = globalIndex * intervalSec;

                    // ✅ 순서: (ecg_value, timestamp)
                    sb.append("(")
                            .append(data.getEcgValue()).append(", ")
                            .append(syntheticTimestamp)
                            .append(") ");
                }
            }

            writer.write(sb.toString().trim());
            writer.flush();

            Log.d(TAG, "✅ ECG data saved to " + file.getAbsolutePath());
            return file;
        } catch (IOException e) {
            Log.e(TAG, "❌ 파일 저장 중 오류", e);
            return null;
        }
    }

    private void showServerResponse(String result) {
        runOnUiThread(() -> {
            try {
                String translatedStatus = result.equals("normal") ? "정상" :
                        result.equals("abnormal") ? "의상 소견 의심" : "알 수 없음";

                new AlertDialog.Builder(EcgActivity.this)
                        .setTitle("분석 결과")
                        .setMessage("분석 결과: " + translatedStatus)
                        .setPositiveButton("확인", (dialog, which) -> {
                            dialog.dismiss();
                            Intent intent = new Intent(EcgActivity.this, EcgInfoActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            startActivity(intent);
                            finish();
                        })
                        .setCancelable(false)
                        .show();
            } catch (Exception e) {
                Log.e(TAG, "❌ 결과 다이얼로그 처리 중 오류", e);
                new AlertDialog.Builder(EcgActivity.this)
                        .setTitle("오류")
                        .setMessage("결과를 분석할 수 없습니다.")
                        .setPositiveButton("확인", (dialog, which) -> dialog.dismiss())
                        .setCancelable(false)
                        .show();
            }
        });
    }

    private AlertDialog progressDialog;

    private void showProgressDialog() {
        LayoutInflater inflater = LayoutInflater.from(EcgActivity.this);
        View progressView = inflater.inflate(R.layout.activity_data_sending, null);

        progressDialog = new AlertDialog.Builder(EcgActivity.this)
                .setView(progressView)
                .setCancelable(false)
                .create();

        progressDialog.show();
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void sendFileToPhone(File file, String result, String resultJson) {
        if (file == null || !file.exists()) {
            Log.e(TAG, "파일이 존재하지 않음");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder fileContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                fileContent.append(line).append("\n");
            }
            Log.d(TAG, "📤 워치에서 전송할 ECG 파일 내용:\n" + fileContent.toString().trim());
        } catch (IOException e) {
            Log.e(TAG, "❌ 파일 읽기 오류", e);
        }

        Asset asset = createAssetFromFile(file);
        if (asset == null) {
            Log.e(TAG, "Asset 변환 실패");
            return;
        }

        long epochMillis = System.currentTimeMillis();

        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/ecg_file");
        putDataMapRequest.getDataMap().putAsset("ecg_data", asset);
        putDataMapRequest.getDataMap().putLong("timestamp", epochMillis);         // ✅ 올바른 시간 전송
        putDataMapRequest.getDataMap().putString("result", result);
        putDataMapRequest.getDataMap().putString("result_json", resultJson);

        PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();

        Wearable.getDataClient(this).putDataItem(putDataRequest)
                .addOnSuccessListener(dataItem -> Log.d(TAG, "✅ ECG 파일 + 결과 전송 성공!"))
                .addOnFailureListener(e -> Log.e(TAG, "❌ 전송 실패", e));
    }

    private Asset createAssetFromFile(File file) {
        try (InputStream inputStream = new FileInputStream(file);
             ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteStream.write(buffer, 0, bytesRead);
            }
            return Asset.createFromBytes(byteStream.toByteArray());
        } catch (IOException e) {
            Log.e(TAG, "❌ 파일을 Asset으로 변환하는 중 오류 발생", e);
            return null;
        }
    }



}