package mx.eltec.smartheattester;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {

    private BroadcastReceiver wifiConnectionBroadcast;
    private boolean connectionOk = false;
    private boolean scanning = false;
    private boolean testOldPort = false;
    private BroadcastReceiver resultsBroadcast;
    private clientRequestActivity clientRequestActivity;
    List<ScanResult> results= new ArrayList<ScanResult>();;
    private ScanResultsAdapter sra;
    private final byte[] bPrefixC = new byte[]{'E','L','T','E','C','_','1','9','8','5','0','0','0','0','0',0x20};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {           // Android 6.0 y superior.

            View rootView = findViewById(R.id.imageView);
            int flags = rootView.getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            rootView.setSystemUiVisibility(flags);
            getWindow().setStatusBarColor(Color.WHITE);

        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.startScan(null);

    }

    public void startScan(View view){

        if(!scanning){
            if ( ContextCompat.checkSelfPermission( this, android.Manifest.permission.ACCESS_COARSE_LOCATION ) != PackageManager.PERMISSION_GRANTED ) {

                Log.i(">>>>>","PERMISSION DENIED!");
                ActivityCompat.requestPermissions( this, new String[] {  android.Manifest.permission.ACCESS_COARSE_LOCATION  }, 666);

            }else{

                Log.i(">>>>>","PERMISSION OK!");
                scanning = true;

                final ProgressDialog pd = new ProgressDialog(this);
                pd.setIndeterminate(true);
                pd.setMessage("Activando Wi-Fi...");
                pd.setCanceledOnTouchOutside(false);
                pd.setCancelable(false);
                pd.show();

                final WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

                if (!wifi.isWifiEnabled()){
                    Log.i(">>>>>","WIFI DISABLE!");
                    wifi.setWifiEnabled(true);

                    new Thread(new Runnable() {
                        @Override
                        public void run() {

                            try {
                                Thread.sleep(7000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                            Handler mainHandler = new Handler(getMainLooper());
                            Runnable myRunnable = new Runnable() {
                                @Override
                                public void run() {

                                    pd.setMessage("Buscando redes SmartHeat/miCalorex...");
                                    results.clear();

                                    final WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

                                    resultsBroadcast = new BroadcastReceiver() {
                                        @Override
                                        public void onReceive(Context context, Intent intent) {

                                            results.addAll(wifiManager.getScanResults());

                                            List<ScanResult> validResults = new ArrayList<ScanResult>();

                                            for(int i = 0; i < results.size(); i++){
                                                if(results.get(i).SSID.startsWith("SmartHeat_") || results.get(i).SSID.startsWith("miCalorex_")){
                                                    boolean repeated = false;
                                                    for(ScanResult tempResult : validResults){
                                                        if(tempResult.SSID.equals(results.get(i).SSID)){
                                                            repeated = true;
                                                            break;
                                                        }
                                                    }
                                                    if(!repeated){
                                                        validResults.add(results.get(i));
                                                    }
                                                }
                                            }

                                            if(validResults.size() > 0){

                                                Comparator<ScanResult> signalComparator = new Comparator<ScanResult>() {
                                                    @Override
                                                    public int compare(ScanResult lhs, ScanResult rhs) {
                                                        if(lhs.level > rhs.level){
                                                            return -1;
                                                        }else if(lhs.level < rhs.level){
                                                            return 1;
                                                        }else{
                                                            return 0;
                                                        }
                                                    }
                                                };

                                                Collections.sort(validResults, signalComparator);

                                                scanning = false;
                                                pd.dismiss();
                                                showResults(validResults);

                                            }else{
                                                scanning = false;
                                                pd.dismiss();
                                                noResults();
                                                boolean locationAdvice = false;
                                                int locationEnable = 0;
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                                                    try {
                                                        locationEnable = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
                                                        if(locationEnable == Settings.Secure.LOCATION_MODE_OFF){
                                                            locationAdvice = true;
                                                        }
                                                    } catch (Settings.SettingNotFoundException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                                if(locationAdvice){

                                                    final AlertDialog.Builder bSingle = new AlertDialog.Builder(MainActivity.this);
                                                    bSingle.setTitle("Ubicación desactivada");
                                                    bSingle.setMessage("En Android 6.0 y versiones posteriores puede ser necesario activar el servicio de ubicación para poder encontrar redes");
                                                    bSingle.setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
                                                        @Override
                                                        public void onClick(DialogInterface dialogInterface, int i) {
                                                            dialogInterface.dismiss();
                                                        }
                                                    });
                                                    bSingle.setNegativeButton("Ajustes de ubicación", new DialogInterface.OnClickListener() {
                                                        @Override
                                                        public void onClick(DialogInterface dialogInterface, int i) {
                                                            dialogInterface.dismiss();
                                                            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                                                        }
                                                    });
                                                    bSingle.show();
                                                }
                                            }
                                        }
                                    };

                                    wifiManager.startScan();
                                    registerReceiver(resultsBroadcast, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
                                    Log.i(">>>>>","REGISTER2");


                                    new CountDownTimer(5000, 5000) {
                                        @Override
                                        public void onTick(long l) {
                                            //wifiManager.startScan();
                                        }

                                        @Override
                                        public void onFinish() {
                                            pd.dismiss();
                                            try{
                                                unregisterReceiver(resultsBroadcast);
                                            }catch (IllegalArgumentException e){
                                                e.printStackTrace();
                                            }
                                            Log.i(">>>>>","UNREGISTER2");
                                            scanning = false;
                                        }
                                    }.start();

                                }
                            };
                            mainHandler.post(myRunnable);

                        }
                    }).start();

                }else{
                    Log.i(">>>>>","WIFI ENABLE!");
                    pd.setMessage("Buscando redes SmartHeat/miCalorex...");
                    results.clear();

                    final WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

                    resultsBroadcast = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {

                            results.addAll(wifiManager.getScanResults());

                            List<ScanResult> validResults = new ArrayList<ScanResult>();

                            for(int i = 0; i < results.size(); i++){
                                if(results.get(i).SSID.startsWith("SmartHeat_") || results.get(i).SSID.startsWith("miCalorex_")){
                                    boolean repeated = false;
                                    for(ScanResult tempResult : validResults){
                                        if(tempResult.SSID.equals(results.get(i).SSID)){
                                            repeated = true;
                                            break;
                                        }
                                    }
                                    if(!repeated){
                                        validResults.add(results.get(i));
                                    }
                                }
                            }

                            if(validResults.size() > 0){

                                Comparator<ScanResult> signalComparator = new Comparator<ScanResult>() {
                                    @Override
                                    public int compare(ScanResult lhs, ScanResult rhs) {
                                        if(lhs.level > rhs.level){
                                            return -1;
                                        }else if(lhs.level < rhs.level){
                                            return 1;
                                        }else{
                                            return 0;
                                        }
                                    }
                                };

                                Collections.sort(validResults, signalComparator);

                                scanning = false;
                                pd.dismiss();
                                showResults(validResults);

                            }else{
                                scanning = false;
                                pd.dismiss();
                                noResults();
                                boolean locationAdvice = false;
                                int locationEnable = 0;
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                                    try {
                                        locationEnable = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
                                        if(locationEnable == Settings.Secure.LOCATION_MODE_OFF){
                                            locationAdvice = true;
                                        }
                                    } catch (Settings.SettingNotFoundException e) {
                                        e.printStackTrace();
                                    }
                                }
                                if(locationAdvice){

                                    final AlertDialog.Builder bSingle = new AlertDialog.Builder(MainActivity.this);
                                    bSingle.setTitle("Ubicación desactivada");
                                    bSingle.setMessage("En Android 6.0 y versiones posteriores puede ser necesario activar el servicio de ubicación para poder encontrar redes");
                                    bSingle.setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            dialogInterface.dismiss();
                                        }
                                    });
                                    bSingle.setNegativeButton("Ajustes de ubicación", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            dialogInterface.dismiss();
                                            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                                        }
                                    });
                                    bSingle.show();
                                }
                            }
                        }
                    };

                    wifiManager.startScan();
                    registerReceiver(resultsBroadcast, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
                    Log.i(">>>>>","REGISTER2");

                    new CountDownTimer(5000, 5000) {
                        @Override
                        public void onTick(long l) {
                            //wifiManager.startScan();
                        }

                        @Override
                        public void onFinish() {
                            pd.dismiss();
                            try{
                                unregisterReceiver(resultsBroadcast);
                            }catch (IllegalArgumentException e){
                                e.printStackTrace();
                            }
                            Log.i(">>>>>","UNREGISTER2");
                            scanning = false;

                            SharedPreferences sp = getSharedPreferences("basic_settings",MODE_PRIVATE);
                            if(sp.getBoolean("show_help", true)){
                                TapTargetView.showFor(MainActivity.this,
                                        TapTarget.forView(findViewById(R.id.retryScanImageButton),
                                                "Actualizar lista de equipos",
                                                "Puede buscar redes nuevamente para encontrar equipos nuevos o dejar de ver equipos fuera de alcance")
                                                .transparentTarget(true)
                                                .cancelable(true)
                                                .outerCircleColor(R.color.colorAccent)
                                                .outerCircleAlpha(1f),
                                        new TapTargetView.Listener() {          // The listener can listen for regular clicks, long clicks or cancels
                                            @Override
                                            public void onTargetClick(TapTargetView view) {
                                                super.onTargetClick(view);
                                                startScan(null);
                                            }
                                        });
                                SharedPreferences.Editor spEdit = sp.edit();
                                spEdit.putBoolean("show_help", false);
                                spEdit.apply();
                            }
                        }
                    }.start();
                }
            }
        }
    }

    private void showResults(List<ScanResult> results){

        findViewById(R.id.resultsLayout).setVisibility(View.VISIBLE);
        findViewById(R.id.noResultsLayout).setVisibility(View.GONE);

        AnimationSet aSet = new AnimationSet(true);
        AlphaAnimation aAnim = new AlphaAnimation(0,1);
        TranslateAnimation tAnim = new TranslateAnimation(0,0,200,0);
        aAnim.setDuration(2000);
        tAnim.setDuration(1400);
        aSet.addAnimation(aAnim);
        aSet.addAnimation(tAnim);
        //aSet.setDuration(600);
        aSet.setInterpolator(new DecelerateInterpolator());
        //findViewById(R.id.scanResultsRecyclerView).startAnimation(aSet);

        this.sra = new ScanResultsAdapter(this, results);

        RecyclerView resultsList = ((RecyclerView) findViewById(R.id.scanResultsRecyclerView));
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
        resultsList.setLayoutManager(mLayoutManager);
        resultsList.setItemAnimator(new DefaultItemAnimator());
        resultsList.setAdapter(this.sra);

        SharedPreferences sp = getSharedPreferences("basic_settings",MODE_PRIVATE);
        if(sp.getBoolean("show_help", true)){
            TapTargetView.showFor(this,
                    TapTarget.forView(findViewById(R.id.retryScanImageButton),
                            "Actualizar lista de equipos",
                            "Puede buscar redes nuevamente para encontrar equipos nuevos o dejar de ver equipos fuera de alcance")
                            .transparentTarget(true)
                            .cancelable(true)
                            .outerCircleColor(R.color.colorAccent)
                            .outerCircleAlpha(1f),
                    new TapTargetView.Listener() {          // The listener can listen for regular clicks, long clicks or cancels
                        @Override
                        public void onTargetClick(TapTargetView view) {
                            super.onTargetClick(view);
                            startScan(null);
                        }
                    });
            SharedPreferences.Editor spEdit = sp.edit();
            spEdit.putBoolean("show_help", false);
            spEdit.apply();
        }

    }

    private void noResults(){

        findViewById(R.id.resultsLayout).setVisibility(View.GONE);
        findViewById(R.id.noResultsLayout).setVisibility(View.VISIBLE);

        SharedPreferences sp = getSharedPreferences("basic_settings",MODE_PRIVATE);
        if(sp.getBoolean("show_help", true)){
            TapTargetView.showFor(this,
                    TapTarget.forView(findViewById(R.id.retryScanImageButton),
                            "Actualizar lista de equipos",
                            "Puede buscar redes nuevamente para encontrar equipos nuevos o dejar de ver equipos fuera de alcance")
                            .transparentTarget(true)
                            .cancelable(true)
                            .outerCircleColor(R.color.colorAccent)
                            .outerCircleAlpha(1f),
                    new TapTargetView.Listener() {          // The listener can listen for regular clicks, long clicks or cancels
                        @Override
                        public void onTargetClick(TapTargetView view) {
                            super.onTargetClick(view);
                            startScan(null);
                        }
                    });
            SharedPreferences.Editor spEdit = sp.edit();
            spEdit.putBoolean("show_help", false);
            spEdit.apply();
        }
    }

    public void startTest(View view){

        // COMPROBAR EXISTENCIA DE LA RED.

        final WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi.isWifiEnabled()){

            // MOSTRAR DIALOGO.

            LayoutInflater inflater = getLayoutInflater();
            final AlertDialog.Builder builderSingle = new AlertDialog.Builder(this);
            final View dialogView = inflater.inflate(R.layout.test_dialog_view, null);

            ((TextView)dialogView.findViewById(R.id.testTitleTextView)).setText("Prueba de \n" + sra.selectedSSID);

            builderSingle.setView(dialogView);
            builderSingle.setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    startScan(null);
                }
            });
            final AlertDialog ad = builderSingle.create();
            ad.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    ad.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                }
            });
            ad.setCancelable(false);
            ad.setCanceledOnTouchOutside(false);
            ad.show();

            // Se configura la tarea para intentar la conexión.
            final WifiManager wManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            final WifiInfo wInfo = wManager.getConnectionInfo();

            final WifiConnectionTask wifiConnectionTask = new WifiConnectionTask();
            wifiConnectionTask.ssid = String.format("\"%s\"", sra.selectedSSID);
            wifiConnectionTask.context = this;
            String ssid = String.format("\"%s\"", sra.selectedSSID);

            if(wInfo.getSSID().contains(ssid)) {
                wifiConnectionTask.ready = true;
            }else {
                wifiConnectionTask.ready = false;
            }
            // Se configura el conteo de TIMEOUT para la conexión.

            final CountDownTimer connTimer = new CountDownTimer(40000, 40000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    /*wifiConnectionTask.cancel(true);
                    final WifiConnectionTask wifiConnectionTask2 = new WifiConnectionTask();
                    wifiConnectionTask2.ssid = String.format("\"%s\"", sra.selectedSSID);
                    wifiConnectionTask2.context = MainActivity.this;
                    wifiConnectionTask2.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);*/

                }

                @Override
                public void onFinish() {

                    // Conexión erronea.

                    dialogView.findViewById(R.id.progressBar1).setVisibility(View.GONE);
                    dialogView.findViewById(R.id.statusImageView1).setVisibility(View.VISIBLE);
                    ((ImageView) dialogView.findViewById(R.id.statusImageView1)).setImageResource(R.drawable.ic_error_red_24dp);
                    ((TextView) dialogView.findViewById(R.id.statusText1)).setText("No se pudo establecer conexión.(timeout)");
                    ad.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                    if(wifiConnectionBroadcast != null ){
                        try{
                            unregisterReceiver(wifiConnectionBroadcast);
                        }catch(IllegalArgumentException e){
                            e.printStackTrace();
                        }
                    }
                    forgetConnection();
                }
            };

            // Se registra el Broadcast paa detectar una conexión correcta.

            wifiConnectionBroadcast = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {

                    NetworkInfo netInfo = intent.getParcelableExtra (WifiManager.EXTRA_NETWORK_INFO);

                    if(netInfo.getType() == ConnectivityManager.TYPE_WIFI && netInfo.isConnected() && netInfo.isConnectedOrConnecting()) {

                        WifiManager wManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                        if(wManager != null){
                            WifiInfo wInfo = wManager.getConnectionInfo();
                            if(!connectionOk
                                    && wInfo.getSSID().contains(sra.selectedSSID)
                                    && netInfo.getState() == NetworkInfo.State.CONNECTED){

                                connTimer.cancel();
                                try{
                                    unregisterReceiver(wifiConnectionBroadcast);
                                }catch(IllegalArgumentException e){
                                    e.printStackTrace();
                                }
                                dialogView.findViewById(R.id.progressBar1).setVisibility(View.GONE);
                                dialogView.findViewById(R.id.statusImageView1).setVisibility(View.VISIBLE);
                                ((ImageView) dialogView.findViewById(R.id.statusImageView1)).setImageResource(R.drawable.ic_check_circle_green_24dp);
                                dialogView.findViewById(R.id.step2Layout).setVisibility(View.VISIBLE);
                                ((TextView) dialogView.findViewById(R.id.statusText1)).setText("Conexión establecida correctamente.");
                                ((TextView) dialogView.findViewById(R.id.statusText2)).setText("Iniciando comunicación con el equipo...");
                                dialogView.findViewById(R.id.statusImageView2).setVisibility(View.GONE);
                                dialogView.findViewById(R.id.progressBar2).setVisibility(View.VISIBLE);

                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {

                                        byte[] bytesToSend = new byte[320];
                                        Arrays.fill(bytesToSend, (byte) 0x00 );
                                        for(int i = 0; i < 16; i++){
                                            bytesToSend[i] = bPrefixC[i];
                                        }

                                        bytesToSend[16] = (byte) 'T';

                                        bytesToSend[17] = 0b0000_0100;

                                        try {
                                            Thread.sleep(100);
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }

                                        final byte[] ok = sendWiFiDirect(bytesToSend, false);

                                        Handler mainHandler = new Handler(getMainLooper());

                                        Runnable myRunnable = new Runnable() {
                                            @Override
                                            public void run() {

                                                if(ok == null){

                                                    if(testOldPort){        // Ya se intentó el puerto 80.

                                                        testOldPort = false;
                                                        ((TextView) dialogView.findViewById(R.id.statusText2)).setText("Error de comunicación (p80).");
                                                        dialogView.findViewById(R.id.statusImageView2).setVisibility(View.VISIBLE);
                                                        ((ImageView) dialogView.findViewById(R.id.statusImageView2)).setImageResource(R.drawable.ic_error_red_24dp);
                                                        dialogView.findViewById(R.id.progressBar2).setVisibility(View.GONE);

                                                        ad.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                                                        forgetConnection();

                                                    }else{                  // Intentar puerto 80.

                                                        new CountDownTimer(2000, 2000) {
                                                            @Override
                                                            public void onTick(long millisUntilFinished) {

                                                            }

                                                            @Override
                                                            public void onFinish() {
                                                                testOldPort = true;

                                                                new Thread(new Runnable() {
                                                                    @Override
                                                                    public void run() {

                                                                        byte[] bytesToSend = new byte[320];
                                                                        Arrays.fill(bytesToSend, (byte) 0x00 );
                                                                        for(int i = 0; i < 16; i++){
                                                                            bytesToSend[i] = bPrefixC[i];
                                                                        }

                                                                        bytesToSend[16] = (byte) 'T';

                                                                        bytesToSend[17] = 0b0000_0100;

                                                                        try {
                                                                            Thread.sleep(100);
                                                                        } catch (InterruptedException e) {
                                                                            e.printStackTrace();
                                                                        }

                                                                        final byte[] ok = sendWiFiDirect(bytesToSend, false);

                                                                        Handler mainHandler = new Handler(getMainLooper());

                                                                        Runnable myRunnable = new Runnable() {
                                                                            @Override
                                                                            public void run() {

                                                                                if(ok == null){

                                                                                    testOldPort = false;
                                                                                    ((TextView) dialogView.findViewById(R.id.statusText2)).setText("Error de comunicación.");
                                                                                    dialogView.findViewById(R.id.statusImageView2).setVisibility(View.VISIBLE);
                                                                                    ((ImageView) dialogView.findViewById(R.id.statusImageView2)).setImageResource(R.drawable.ic_error_red_24dp);
                                                                                    dialogView.findViewById(R.id.progressBar2).setVisibility(View.GONE);

                                                                                    ad.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                                                                                    forgetConnection();

                                                                                }else{

                                                                                    if(ok[0] == '!'){

                                                                                        ((TextView) dialogView.findViewById(R.id.statusText2)).setText("¡Firmware Wi-Fi antiguo 0.5.2 ó anterior! p80.");
                                                                                        ((TextView) dialogView.findViewById(R.id.statusText2)).setTextColor(Color.RED);
                                                                                        dialogView.findViewById(R.id.statusImageView2).setVisibility(View.VISIBLE);
                                                                                        ((ImageView) dialogView.findViewById(R.id.statusImageView2)).setImageResource(R.drawable.ic_error_red_24dp);
                                                                                        dialogView.findViewById(R.id.progressBar2).setVisibility(View.GONE);

                                                                                    }else{

                                                                                        ((TextView) dialogView.findViewById(R.id.statusText2)).setText("Error de comunicación (Contraseña/Checksum).");
                                                                                        ((TextView) dialogView.findViewById(R.id.statusText2)).setTextColor(Color.RED);
                                                                                        dialogView.findViewById(R.id.statusImageView2).setVisibility(View.VISIBLE);
                                                                                        ((ImageView) dialogView.findViewById(R.id.statusImageView2)).setImageResource(R.drawable.ic_error_red_24dp);
                                                                                        dialogView.findViewById(R.id.progressBar2).setVisibility(View.GONE);
                                                                                    }

                                                                                    ad.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                                                                                    forgetConnection();
                                                                                }

                                                                                if(wifiConnectionBroadcast != null ){
                                                                                    try{
                                                                                        unregisterReceiver(wifiConnectionBroadcast);
                                                                                    }catch(IllegalArgumentException e){
                                                                                        e.printStackTrace();
                                                                                    }
                                                                                }

                                                                            }
                                                                        };
                                                                        mainHandler.post(myRunnable);

                                                                    }
                                                                }).start();

                                                            }
                                                        }.start();
                                                    }

                                                }else{

                                                    if(ok[0] == '!'){

                                                        String cFirm = "";
                                                        if(ok[ok.length - 6] != 0){
                                                            float s = ((float)(ok[ok.length - 6] & 0xff)/10f);
                                                            cFirm = String.valueOf(s);
                                                        }

                                                        if(cFirm.isEmpty() /*|| !cFirm.equals("2.3")*/){
                                                            ((TextView) dialogView.findViewById(R.id.statusText2)).setText("Firmware incorrecto (" + cFirm + ") o nulo");
                                                            ((TextView) dialogView.findViewById(R.id.statusText2)).setTextColor(Color.RED);
                                                            dialogView.findViewById(R.id.statusImageView2).setVisibility(View.VISIBLE);
                                                            ((ImageView) dialogView.findViewById(R.id.statusImageView2)).setImageResource(R.drawable.ic_error_red_24dp);
                                                            dialogView.findViewById(R.id.progressBar2).setVisibility(View.GONE);
                                                        }else{
                                                            ((TextView) dialogView.findViewById(R.id.statusText2)).setText("Prueba terminada:"+cFirm);
                                                            dialogView.findViewById(R.id.statusImageView2).setVisibility(View.VISIBLE);
                                                            ((ImageView) dialogView.findViewById(R.id.statusImageView2)).setImageResource(R.drawable.ic_check_circle_green_24dp);
                                                            dialogView.findViewById(R.id.progressBar2).setVisibility(View.GONE);
                                                        }

                                                    }else{

                                                        ((TextView) dialogView.findViewById(R.id.statusText2)).setText("Error de comunicación (Contraseña/Checksum).");
                                                        ((TextView) dialogView.findViewById(R.id.statusText2)).setTextColor(Color.RED);
                                                        dialogView.findViewById(R.id.statusImageView2).setVisibility(View.VISIBLE);
                                                        ((ImageView) dialogView.findViewById(R.id.statusImageView2)).setImageResource(R.drawable.ic_error_red_24dp);
                                                        dialogView.findViewById(R.id.progressBar2).setVisibility(View.GONE);
                                                    }

                                                    ad.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                                                    forgetConnection();
                                                }

                                                if(wifiConnectionBroadcast != null ){
                                                    try{
                                                        unregisterReceiver(wifiConnectionBroadcast);
                                                    }catch(IllegalArgumentException e){
                                                        e.printStackTrace();
                                                    }
                                                }

                                            }
                                        };
                                        mainHandler.post(myRunnable);

                                    }
                                }).start();
                            }
                        }
                    }
                }
            };

            registerReceiver(wifiConnectionBroadcast, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));

            // Se inicia el intento de conexión a la red encontrada.

            wifiConnectionTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

            // Se inicia el conteo de TIMEOUT para el intento de conexión.

            connTimer.start();


        }else{

            final AlertDialog.Builder bSingle = new AlertDialog.Builder(this);
            bSingle.setTitle("Wi-Fi Desactivado");
            bSingle.setMessage("Active el Wi-Fi de este dispositivo e intentelo nuevamente");
            bSingle.setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            });
            bSingle.show();
        }

        /*if (!wifi.isWifiEnabled()){

            wifi.setWifiEnabled(true);

            new Thread(new Runnable() {
                @Override
                public void run() {

                    try {
                        Thread.sleep(7000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    Handler mainHandler = new Handler(getMainLooper());
                    Runnable myRunnable = new Runnable() {
                        @Override
                        public void run() {

                            final WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

                            resultsBroadcast = new BroadcastReceiver() {
                                @Override
                                public void onReceive(Context context, Intent intent) {

                                    results = wifi.getScanResults();

                                    unregisterReceiver(resultsBroadcast);

                                    boolean selectedNetworkFound = false;

                                    for(int i = 0; i < results.size(); i++){
                                        if(results.get(i).SSID.contains(sra.selectedSSID)){
                                            selectedNetworkFound = true;

                                            // CONECTAR.

                                            if(wifiConnectionBroadcast == null){
                                                wifiConnectionBroadcast = new BroadcastReceiver() {
                                                    @Override
                                                    public void onReceive(Context context, Intent intent) {
                                                        Bundle extras = intent.getExtras();
                                                        NetworkInfo info = extras.getParcelable("networkInfo");
                                                        NetworkInfo.State state = info.getState();
                                                        if (state == NetworkInfo.State.CONNECTED) {
                                                            WifiManager wManager = (WifiManager)context.getSystemService(context.WIFI_SERVICE);
                                                            WifiInfo wInfo = wManager.getConnectionInfo();
                                                            ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                                                            NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                                                            if (mWifi.getState() == NetworkInfo.State.CONNECTED && wInfo.getSSID().contains(sra.selectedSSID)) {           // Conexión exitosa.
                                                                connectionOk = true;
                                                            }
                                                        }
                                                    }
                                                };
                                            }

                                            registerReceiver(
                                                    wifiConnectionBroadcast,
                                                    new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

                                            final WifiConnectionTask wifiConnectionActivity = new WifiConnectionTask();
                                            wifiConnectionActivity.ssid = String.format("\"%s\"", sra.selectedSSID);
                                            wifiConnectionActivity.context = getApplication();

                                            wifiConnectionActivity.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                                            new CountDownTimer(15000, 500) {

                                                boolean done = false;
                                                boolean executed = false;

                                                public void onTick(long millisUntilFinished) {
                                                    if(connectionOk) {
                                                        done = true;
                                                        this.onFinish();
                                                    }
                                                }

                                                public void onFinish() {

                                                    if(!executed){// && connectionOk){
                                                        executed = true;
                                                        if(wifiConnectionBroadcast != null ){
                                                            try{
                                                                unregisterReceiver(wifiConnectionBroadcast);
                                                            }catch(IllegalArgumentException e){
                                                                e.printStackTrace();
                                                            }
                                                        }
                                                        wifiConnectionActivity.cancel(true);
                                                        final WifiManager wManager =
                                                                (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
                                                        WifiInfo wInfo = wManager.getConnectionInfo();

                                                        ConnectivityManager connManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                                                        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

                                                        if (done || mWifi.isConnected() && wInfo.getSSID().equals(sra.selectedSSID)
                                                                || String.format("\"%s\"", wInfo.getSSID())
                                                                .equals(sra.selectedSSID)
                                                                || wInfo.getSSID().equals(String.format("\"%s\"",
                                                                sra.selectedSSID))
                                                                || String.format("\"%s\"", wInfo.getSSID())
                                                                .equals(String.format("\"%s\"", sra.selectedSSID))) {                           // Conexión exitosa.

                                                            dialogView.findViewById(R.id.progressBar1).setVisibility(View.GONE);
                                                            dialogView.findViewById(R.id.statusImageView1).setVisibility(View.VISIBLE);
                                                            ((ImageView) dialogView.findViewById(R.id.statusImageView1)).setImageResource(R.drawable.icon_done);
                                                            dialogView.findViewById(R.id.step2Layout).setVisibility(View.VISIBLE);
                                                            ((TextView) dialogView.findViewById(R.id.statusText1)).setText("Conexión establecida correctamente.");
                                                            ((TextView) dialogView.findViewById(R.id.statusText2)).setText("Iniciando comunicación con el equipo...");
                                                            dialogView.findViewById(R.id.statusImageView2).setVisibility(View.GONE);
                                                            dialogView.findViewById(R.id.progressBar2).setVisibility(View.VISIBLE);

                                                            new Thread(new Runnable() {
                                                                @Override
                                                                public void run() {

                                                                    byte[] bytesToSend = new byte[320];
                                                                    Arrays.fill(bytesToSend, (byte) 0x00 );
                                                                    for(int i = 0; i < 16; i++){
                                                                        bytesToSend[i] = bPrefixC[i];
                                                                    }

                                                                    bytesToSend[16] = (byte) 'T';

                                                                    bytesToSend[17] = 0b0000_0000;

                                                                    try {
                                                                        Thread.sleep(100);
                                                                    } catch (InterruptedException e) {
                                                                        e.printStackTrace();
                                                                    }

                                                                    final byte[] ok = sendWiFiDirect(bytesToSend, false);

                                                                    Handler mainHandler = new Handler(getMainLooper());

                                                                    Runnable myRunnable = new Runnable() {
                                                                        @Override
                                                                        public void run() {

                                                                            if(ok != null && ok[0] == '!'){

                                                                                ((TextView) dialogView.findViewById(R.id.statusText2)).setText("Prueba terminada.");
                                                                                dialogView.findViewById(R.id.statusImageView2).setVisibility(View.VISIBLE);
                                                                                ((ImageView) dialogView.findViewById(R.id.statusImageView2)).setImageResource(R.drawable.icon_done);
                                                                                dialogView.findViewById(R.id.progressBar2).setVisibility(View.GONE);

                                                                            }else{

                                                                                ((TextView) dialogView.findViewById(R.id.statusText2)).setText("Error de comunicación.");
                                                                                dialogView.findViewById(R.id.statusImageView2).setVisibility(View.VISIBLE);
                                                                                ((ImageView) dialogView.findViewById(R.id.statusImageView2)).setImageResource(R.drawable.icon_fail);
                                                                                dialogView.findViewById(R.id.progressBar2).setVisibility(View.GONE);
                                                                            }

                                                                            if(wifiConnectionBroadcast != null ){
                                                                                try{
                                                                                    unregisterReceiver(wifiConnectionBroadcast);
                                                                                }catch(IllegalArgumentException e){
                                                                                    e.printStackTrace();
                                                                                }
                                                                            }                                        forgetConnection();
                                                                            ad.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);

                                                                        }
                                                                    };
                                                                    mainHandler.post(myRunnable);

                                                                }
                                                            }).start();

                                                        } else {                                                                               // Conexión erronea, reintentar.

                                                            final WifiConnectionTask wifiConnectionActivity = new WifiConnectionTask();
                                                            wifiConnectionActivity.ssid = String.format("\"%s\"", sra.selectedSSID);
                                                            wifiConnectionActivity.context = getApplicationContext();

                                                            wifiConnectionActivity.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                                                            wManager.setWifiEnabled(false);

                                                            new CountDownTimer(4000, 4000) {
                                                                @Override
                                                                public void onTick(long millisUntilFinished) {

                                                                }

                                                                @Override
                                                                public void onFinish() {
                                                                    wManager.setWifiEnabled(true);
                                                                    new CountDownTimer(15000, 15000) {

                                                                        boolean done = false;
                                                                        boolean executed = false;

                                                                        public void onTick(long millisUntilFinished) {
                                                                            if(connectionOk) {
                                                                                done = true;
                                                                                this.onFinish();
                                                                            }
                                                                        }

                                                                        public void onFinish() {

                                                                            if(!executed){
                                                                                executed = true;

                                                                                if(wifiConnectionBroadcast != null ){
                                                                                    try{
                                                                                        unregisterReceiver(wifiConnectionBroadcast);
                                                                                    }catch(IllegalArgumentException e){
                                                                                        e.printStackTrace();
                                                                                    }
                                                                                }

                                                                                wifiConnectionActivity.cancel(true);
                                                                                WifiManager wManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                                                                                WifiInfo wInfo = wManager.getConnectionInfo();

                                                                                ConnectivityManager connManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                                                                                NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

                                                                                if (done ||  mWifi.isConnected() && wInfo.getSSID().equals(sra.selectedSSID)
                                                                                        || String.format("\"%s\"", wInfo.getSSID()).equals(sra.selectedSSID)
                                                                                        || wInfo.getSSID().equals(String.format("\"%s\"", sra.selectedSSID))
                                                                                        || String.format("\"%s\"", wInfo.getSSID())
                                                                                        .equals(String.format("\"%s\"", sra.selectedSSID))) {               // Conexión exitosa en reintento.

                                                                                    dialogView.findViewById(R.id.progressBar1).setVisibility(View.GONE);
                                                                                    dialogView.findViewById(R.id.statusImageView1).setVisibility(View.VISIBLE);
                                                                                    ((ImageView) dialogView.findViewById(R.id.statusImageView1)).setImageResource(R.drawable.icon_done);
                                                                                    dialogView.findViewById(R.id.step2Layout).setVisibility(View.VISIBLE);
                                                                                    ((TextView) dialogView.findViewById(R.id.statusText1)).setText("Conexión establecida correctamente.");
                                                                                    ((TextView) dialogView.findViewById(R.id.statusText2)).setText("Iniciando comunicación con el equipo...");
                                                                                    dialogView.findViewById(R.id.statusImageView2).setVisibility(View.GONE);
                                                                                    dialogView.findViewById(R.id.progressBar2).setVisibility(View.VISIBLE);


                                                                                    new Thread(new Runnable() {
                                                                                        @Override
                                                                                        public void run() {

                                                                                            byte[] bytesToSend = new byte[320];
                                                                                            Arrays.fill(bytesToSend, (byte) 0x00 );
                                                                                            for(int i = 0; i < 16; i++){
                                                                                                bytesToSend[i] = bPrefixC[i];
                                                                                            }

                                                                                            bytesToSend[16] = (byte) 'T';

                                                                                            bytesToSend[17] = 0b0000_0100;

                                                                                            try {
                                                                                                Thread.sleep(100);
                                                                                            } catch (InterruptedException e) {
                                                                                                e.printStackTrace();
                                                                                            }

                                                                                            final byte[] ok = sendWiFiDirect(bytesToSend, false);

                                                                                            Handler mainHandler = new Handler(getMainLooper());

                                                                                            Runnable myRunnable = new Runnable() {
                                                                                                @Override
                                                                                                public void run() {

                                                                                                    if(ok != null && ok[0] == '!'){

                                                                                                        ((TextView) dialogView.findViewById(R.id.statusText2)).setText("Prueba terminada.");
                                                                                                        dialogView.findViewById(R.id.statusImageView2).setVisibility(View.VISIBLE);
                                                                                                        ((ImageView) dialogView.findViewById(R.id.statusImageView2)).setImageResource(R.drawable.icon_done);
                                                                                                        dialogView.findViewById(R.id.progressBar2).setVisibility(View.GONE);

                                                                                                    }else{

                                                                                                        ((TextView) dialogView.findViewById(R.id.statusText2)).setText("Error de comunicación.");
                                                                                                        dialogView.findViewById(R.id.statusImageView2).setVisibility(View.VISIBLE);
                                                                                                        ((ImageView) dialogView.findViewById(R.id.statusImageView2)).setImageResource(R.drawable.icon_fail);
                                                                                                        dialogView.findViewById(R.id.progressBar2).setVisibility(View.GONE);
                                                                                                    }

                                                                                                    if(wifiConnectionBroadcast != null ){
                                                                                                        try{
                                                                                                            unregisterReceiver(wifiConnectionBroadcast);
                                                                                                        }catch(IllegalArgumentException e){
                                                                                                            e.printStackTrace();
                                                                                                        }
                                                                                                    }                                                                ad.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                                                                                                    forgetConnection();
                                                                                                }
                                                                                            };
                                                                                            mainHandler.post(myRunnable);

                                                                                        }
                                                                                    }).start();

                                                                                } else {                                                           // Conexión erronea en reintento.

                                                                                    dialogView.findViewById(R.id.progressBar1).setVisibility(View.GONE);
                                                                                    dialogView.findViewById(R.id.statusImageView1).setVisibility(View.VISIBLE);
                                                                                    ((ImageView) dialogView.findViewById(R.id.statusImageView1)).setImageResource(R.drawable.icon_fail);
                                                                                    ((TextView) dialogView.findViewById(R.id.statusText1)).setText("No se pudo establecer conexión.");
                                                                                    ad.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                                                                                    if(wifiConnectionBroadcast != null ){
                                                                                        try{
                                                                                            unregisterReceiver(wifiConnectionBroadcast);
                                                                                        }catch(IllegalArgumentException e){
                                                                                            e.printStackTrace();
                                                                                        }
                                                                                    }
                                                                                    forgetConnection();
                                                                                }
                                                                                this.cancel();
                                                                            }
                                                                        }
                                                                    }.start();
                                                                }
                                                            }.start();
                                                        }
                                                        this.cancel();

                                                    }else if(!executed){                                                                            // CONEXIÓN ERRONEA.

                                                        forgetConnection();
                                                        if(wifiConnectionBroadcast != null ){
                                                            try{
                                                                unregisterReceiver(wifiConnectionBroadcast);
                                                            }catch(IllegalArgumentException e){
                                                                e.printStackTrace();
                                                            }
                                                        }

                                                        dialogView.findViewById(R.id.progressBar1).setVisibility(View.GONE);
                                                        dialogView.findViewById(R.id.statusImageView1).setVisibility(View.VISIBLE);
                                                        ((ImageView) dialogView.findViewById(R.id.statusImageView1)).setImageResource(R.drawable.icon_fail);
                                                        ((TextView) dialogView.findViewById(R.id.statusText1)).setText("No se pudo establecer conexión.");
                                                        ad.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                                                    }
                                                }
                                            }.start();
                                        }
                                    }

                                    if(!selectedNetworkFound){
                                        dialogView.findViewById(R.id.progressBar1).setVisibility(View.GONE);
                                        dialogView.findViewById(R.id.statusImageView1).setVisibility(View.VISIBLE);
                                        ((ImageView) dialogView.findViewById(R.id.statusImageView1)).setImageResource(R.drawable.icon_fail);
                                        ((TextView) dialogView.findViewById(R.id.statusText1)).setText("No se pudo establecer conexión.");
                                        ad.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                                    }
                                }
                            };
                            registerReceiver(resultsBroadcast, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
                            wifi.startScan();

                        }
                    };
                    mainHandler.post(myRunnable);

                }
            }).start();

        }else{

            resultsBroadcast = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {

                    results = wifi.getScanResults();

                    unregisterReceiver(resultsBroadcast);

                    boolean selectedNetworkFound = false;

                    for(int i = 0; i < results.size(); i++){
                        if(results.get(i).SSID.contains(sra.selectedSSID)){
                            selectedNetworkFound = true;

                            // CONECTAR.

                            if(wifiConnectionBroadcast == null){
                                wifiConnectionBroadcast = new BroadcastReceiver() {
                                    @Override
                                    public void onReceive(Context context, Intent intent) {
                                        Bundle extras = intent.getExtras();
                                        NetworkInfo info = extras.getParcelable("networkInfo");
                                        NetworkInfo.State state = info.getState();
                                        if (state == NetworkInfo.State.CONNECTED) {
                                            WifiManager wManager = (WifiManager)context.getSystemService(context.WIFI_SERVICE);
                                            WifiInfo wInfo = wManager.getConnectionInfo();
                                            ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                                            NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                                            if (mWifi.getState() == NetworkInfo.State.CONNECTED && wInfo.getSSID().contains(sra.selectedSSID)) {           // Conexión exitosa.
                                                connectionOk = true;
                                            }
                                        }
                                    }
                                };
                            }

                            registerReceiver(
                                    wifiConnectionBroadcast,
                                    new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

                            final WifiConnectionTask wifiConnectionActivity = new WifiConnectionTask();
                            wifiConnectionActivity.ssid = String.format("\"%s\"", sra.selectedSSID);
                            wifiConnectionActivity.context = getApplicationContext();

                            wifiConnectionActivity.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                            new CountDownTimer(15000, 500) {

                                boolean done = false;
                                boolean executed = false;

                                public void onTick(long millisUntilFinished) {
                                    if(connectionOk) {
                                        done = true;
                                        this.onFinish();
                                    }
                                }

                                public void onFinish() {

                                    if(!executed){// && connectionOk){
                                        executed = true;
                                        if(wifiConnectionBroadcast != null ){
                                            try{
                                                unregisterReceiver(wifiConnectionBroadcast);
                                            }catch(IllegalArgumentException e){
                                                e.printStackTrace();
                                            }
                                        }
                                        wifiConnectionActivity.cancel(true);
                                        final WifiManager wManager =
                                                (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
                                        WifiInfo wInfo = wManager.getConnectionInfo();

                                        ConnectivityManager connManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                                        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

                                        if (done || mWifi.isConnected() && wInfo.getSSID().equals(sra.selectedSSID)
                                                || String.format("\"%s\"", wInfo.getSSID())
                                                .equals(sra.selectedSSID)
                                                || wInfo.getSSID().equals(String.format("\"%s\"",
                                                sra.selectedSSID))
                                                || String.format("\"%s\"", wInfo.getSSID())
                                                .equals(String.format("\"%s\"", sra.selectedSSID))) {                           // Conexión exitosa.

                                            dialogView.findViewById(R.id.progressBar1).setVisibility(View.GONE);
                                            dialogView.findViewById(R.id.statusImageView1).setVisibility(View.VISIBLE);
                                            ((ImageView) dialogView.findViewById(R.id.statusImageView1)).setImageResource(R.drawable.icon_done);
                                            dialogView.findViewById(R.id.step2Layout).setVisibility(View.VISIBLE);
                                            ((TextView) dialogView.findViewById(R.id.statusText1)).setText("Conexión establecida correctamente.");
                                            ((TextView) dialogView.findViewById(R.id.statusText2)).setText("Iniciando comunicación con el equipo...");
                                            dialogView.findViewById(R.id.statusImageView2).setVisibility(View.GONE);
                                            dialogView.findViewById(R.id.progressBar2).setVisibility(View.VISIBLE);

                                            new Thread(new Runnable() {
                                                @Override
                                                public void run() {

                                                    byte[] bytesToSend = new byte[320];
                                                    Arrays.fill(bytesToSend, (byte) 0x00 );
                                                    for(int i = 0; i < 16; i++){
                                                        bytesToSend[i] = bPrefixC[i];
                                                    }

                                                    bytesToSend[16] = (byte) 'T';

                                                    bytesToSend[17] = 0b0000_0000;

                                                    try {
                                                        Thread.sleep(100);
                                                    } catch (InterruptedException e) {
                                                        e.printStackTrace();
                                                    }

                                                    final byte[] ok = sendWiFiDirect(bytesToSend, false);

                                                    Handler mainHandler = new Handler(getMainLooper());

                                                    Runnable myRunnable = new Runnable() {
                                                        @Override
                                                        public void run() {

                                                            if(ok != null && ok[0] == '!'){

                                                                ((TextView) dialogView.findViewById(R.id.statusText2)).setText("Prueba terminada.");
                                                                dialogView.findViewById(R.id.statusImageView2).setVisibility(View.VISIBLE);
                                                                ((ImageView) dialogView.findViewById(R.id.statusImageView2)).setImageResource(R.drawable.icon_done);
                                                                dialogView.findViewById(R.id.progressBar2).setVisibility(View.GONE);

                                                            }else{

                                                                ((TextView) dialogView.findViewById(R.id.statusText2)).setText("Error de comunicación.");
                                                                dialogView.findViewById(R.id.statusImageView2).setVisibility(View.VISIBLE);
                                                                ((ImageView) dialogView.findViewById(R.id.statusImageView2)).setImageResource(R.drawable.icon_fail);
                                                                dialogView.findViewById(R.id.progressBar2).setVisibility(View.GONE);
                                                            }

                                                            if(wifiConnectionBroadcast != null ){
                                                                try{
                                                                    unregisterReceiver(wifiConnectionBroadcast);
                                                                }catch(IllegalArgumentException e){
                                                                    e.printStackTrace();
                                                                }
                                                            }                                        forgetConnection();
                                                            ad.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);

                                                        }
                                                    };
                                                    mainHandler.post(myRunnable);

                                                }
                                            }).start();

                                        } else {                                                                               // Conexión erronea, reintentar.

                                            final WifiConnectionTask wifiConnectionActivity = new WifiConnectionTask();
                                            wifiConnectionActivity.ssid = String.format("\"%s\"", sra.selectedSSID);
                                            wifiConnectionActivity.context = getApplicationContext();

                                            wifiConnectionActivity.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                                            wManager.setWifiEnabled(false);

                                            new CountDownTimer(4000, 4000) {
                                                @Override
                                                public void onTick(long millisUntilFinished) {

                                                }

                                                @Override
                                                public void onFinish() {
                                                    wManager.setWifiEnabled(true);
                                                    new CountDownTimer(15000, 15000) {

                                                        boolean done = false;
                                                        boolean executed = false;

                                                        public void onTick(long millisUntilFinished) {
                                                            if(connectionOk) {
                                                                done = true;
                                                                this.onFinish();
                                                            }
                                                        }

                                                        public void onFinish() {

                                                            if(!executed){
                                                                executed = true;

                                                                if(wifiConnectionBroadcast != null ){
                                                                    try{
                                                                        unregisterReceiver(wifiConnectionBroadcast);
                                                                    }catch(IllegalArgumentException e){
                                                                        e.printStackTrace();
                                                                    }
                                                                }

                                                                wifiConnectionActivity.cancel(true);
                                                                WifiManager wManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                                                                WifiInfo wInfo = wManager.getConnectionInfo();

                                                                ConnectivityManager connManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                                                                NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

                                                                if (done ||  mWifi.isConnected() && wInfo.getSSID().equals(sra.selectedSSID)
                                                                        || String.format("\"%s\"", wInfo.getSSID()).equals(sra.selectedSSID)
                                                                        || wInfo.getSSID().equals(String.format("\"%s\"", sra.selectedSSID))
                                                                        || String.format("\"%s\"", wInfo.getSSID())
                                                                        .equals(String.format("\"%s\"", sra.selectedSSID))) {               // Conexión exitosa en reintento.

                                                                    dialogView.findViewById(R.id.progressBar1).setVisibility(View.GONE);
                                                                    dialogView.findViewById(R.id.statusImageView1).setVisibility(View.VISIBLE);
                                                                    ((ImageView) dialogView.findViewById(R.id.statusImageView1)).setImageResource(R.drawable.icon_done);
                                                                    dialogView.findViewById(R.id.step2Layout).setVisibility(View.VISIBLE);
                                                                    ((TextView) dialogView.findViewById(R.id.statusText1)).setText("Conexión establecida correctamente.");
                                                                    ((TextView) dialogView.findViewById(R.id.statusText2)).setText("Iniciando comunicación con el equipo...");
                                                                    dialogView.findViewById(R.id.statusImageView2).setVisibility(View.GONE);
                                                                    dialogView.findViewById(R.id.progressBar2).setVisibility(View.VISIBLE);


                                                                    new Thread(new Runnable() {
                                                                        @Override
                                                                        public void run() {

                                                                            byte[] bytesToSend = new byte[320];
                                                                            Arrays.fill(bytesToSend, (byte) 0x00 );
                                                                            for(int i = 0; i < 16; i++){
                                                                                bytesToSend[i] = bPrefixC[i];
                                                                            }

                                                                            bytesToSend[16] = (byte) 'T';

                                                                            bytesToSend[17] = 0b0000_0100;

                                                                            try {
                                                                                Thread.sleep(100);
                                                                            } catch (InterruptedException e) {
                                                                                e.printStackTrace();
                                                                            }

                                                                            final byte[] ok = sendWiFiDirect(bytesToSend, false);

                                                                            Handler mainHandler = new Handler(getMainLooper());

                                                                            Runnable myRunnable = new Runnable() {
                                                                                @Override
                                                                                public void run() {

                                                                                    if(ok != null && ok[0] == '!'){

                                                                                        ((TextView) dialogView.findViewById(R.id.statusText2)).setText("Prueba terminada.");
                                                                                        dialogView.findViewById(R.id.statusImageView2).setVisibility(View.VISIBLE);
                                                                                        ((ImageView) dialogView.findViewById(R.id.statusImageView2)).setImageResource(R.drawable.icon_done);
                                                                                        dialogView.findViewById(R.id.progressBar2).setVisibility(View.GONE);

                                                                                    }else{

                                                                                        ((TextView) dialogView.findViewById(R.id.statusText2)).setText("Error de comunicación.");
                                                                                        dialogView.findViewById(R.id.statusImageView2).setVisibility(View.VISIBLE);
                                                                                        ((ImageView) dialogView.findViewById(R.id.statusImageView2)).setImageResource(R.drawable.icon_fail);
                                                                                        dialogView.findViewById(R.id.progressBar2).setVisibility(View.GONE);
                                                                                    }

                                                                                    if(wifiConnectionBroadcast != null ){
                                                                                        try{
                                                                                            unregisterReceiver(wifiConnectionBroadcast);
                                                                                        }catch(IllegalArgumentException e){
                                                                                            e.printStackTrace();
                                                                                        }
                                                                                    }                                                                ad.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                                                                                    forgetConnection();
                                                                                }
                                                                            };
                                                                            mainHandler.post(myRunnable);

                                                                        }
                                                                    }).start();

                                                                } else {                                                           // Conexión erronea en reintento.

                                                                    dialogView.findViewById(R.id.progressBar1).setVisibility(View.GONE);
                                                                    dialogView.findViewById(R.id.statusImageView1).setVisibility(View.VISIBLE);
                                                                    ((ImageView) dialogView.findViewById(R.id.statusImageView1)).setImageResource(R.drawable.icon_fail);
                                                                    ((TextView) dialogView.findViewById(R.id.statusText1)).setText("No se pudo establecer conexión.");
                                                                    ad.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                                                                    if(wifiConnectionBroadcast != null ){
                                                                        try{
                                                                            unregisterReceiver(wifiConnectionBroadcast);
                                                                        }catch(IllegalArgumentException e){
                                                                            e.printStackTrace();
                                                                        }
                                                                    }                                                forgetConnection();
                                                                }
                                                                this.cancel();
                                                            }
                                                        }
                                                    }.start();
                                                }
                                            }.start();
                                        }
                                        this.cancel();

                                    }else if(!executed){                                                                            // CONEXIÓN ERRONEA.

                                        forgetConnection();
                                        if(wifiConnectionBroadcast != null ){
                                            try{
                                                unregisterReceiver(wifiConnectionBroadcast);
                                            }catch(IllegalArgumentException e){
                                                e.printStackTrace();
                                            }
                                        }

                                        dialogView.findViewById(R.id.progressBar1).setVisibility(View.GONE);
                                        dialogView.findViewById(R.id.statusImageView1).setVisibility(View.VISIBLE);
                                        ((ImageView) dialogView.findViewById(R.id.statusImageView1)).setImageResource(R.drawable.icon_fail);
                                        ((TextView) dialogView.findViewById(R.id.statusText1)).setText("No se pudo establecer conexión.");
                                        ad.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                                    }
                                }
                            }.start();

                        }
                    }

                    if(!selectedNetworkFound){
                        dialogView.findViewById(R.id.progressBar1).setVisibility(View.GONE);
                        dialogView.findViewById(R.id.statusImageView1).setVisibility(View.VISIBLE);
                        ((ImageView) dialogView.findViewById(R.id.statusImageView1)).setImageResource(R.drawable.icon_fail);
                        ((TextView) dialogView.findViewById(R.id.statusText1)).setText("No se pudo establecer conexión.");
                        ad.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                    }
                }
            };
            registerReceiver(resultsBroadcast, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
            wifi.startScan();

        }*/

    }

    private byte[] sendWiFiDirect(byte[] toSend, boolean ignoreChecksum){

        byte[] ok = null;

        if(this.clientRequestActivity != null){
            this.clientRequestActivity.cancel(true);
        }

        clientRequestActivity = new clientRequestActivity();
        clientRequestActivity.context = this;
        clientRequestActivity.oldPortNeeded = testOldPort;

        try {

            byte[] bytesToSend = addChecksum(toSend);
            byte[] req = encrypt("1234567890123456", bytesToSend);
            clientRequestActivity.setReqCode(req);
            String resultado = (String) clientRequestActivity.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR).get(15000, TimeUnit.MILLISECONDS);
            this.clientRequestActivity.cancel(true);
            Log.i(">>>>>","RECEIVED: " + resultado);
            if(resultado.contains("WrongPort")){
                return ok;
            }else{
                byte[] ready = decrypt("1234567890123456".getBytes(), Base64.decode(resultado,Base64.DEFAULT));

                if(ready.length > 0){
                    //if(checkChecksum(ready) || ignoreChecksum){
                        ok = ready;
                    //}
                }
            }


        } catch (InterruptedException e) {
        } catch (ExecutionException e) {
        } catch (TimeoutException e) {
        }

        return ok;

    }

    private boolean checkChecksum(byte[] bytes){

        byte[] csBackup = new byte[4];
        csBackup[0] = bytes[bytes.length - 4];
        csBackup[1] = bytes[bytes.length - 3];
        csBackup[2] = bytes[bytes.length - 2];
        csBackup[3] = bytes[bytes.length - 1];

        bytes[bytes.length - 4] = 0x0;
        bytes[bytes.length - 3] = 0x0;
        bytes[bytes.length - 2] = 0x0;
        bytes[bytes.length - 1] = 0x0;


        // Se realiza la suma.
        long cs = 0;
        for (byte aByte : bytes) {
            if(aByte != 0x00) cs += aByte & 0xFF;
        }

        // Se agrega el resultado de la suma en el arreglo final.
        ByteBuffer buffer = ByteBuffer.wrap(new byte[8]);
        buffer.putLong(cs);

        byte[] newBackup = new byte[4];
        newBackup[0] = buffer.array()[7];
        newBackup[1] = buffer.array()[6];
        newBackup[2] = buffer.array()[5];
        newBackup[3] = buffer.array()[4];

        return Arrays.equals(csBackup,newBackup);
    }

    private byte[] addChecksum(byte[] bytes){

        // Se limpian los bytes donde se colocará el checksum.
        bytes[bytes.length - 4] = 0x0;
        bytes[bytes.length - 3] = 0x0;
        bytes[bytes.length - 2] = 0x0;
        bytes[bytes.length - 1] = 0x0;

        byte[] headerBackup = new byte[16];
        if(bytes.length == 320){
            // Se respalda la cabecera del mensaje (16 bytes) y se limpia.
            for(int i = 0; i<16; i++){
                headerBackup[i] = bytes[i];
                bytes[i] = 0x0;
            }
        }

        // Se realiza la suma.
        long cs = 0;
        for (byte aByte : bytes) {
            cs += aByte & 0xFF;
        }

        // Se agrega el resultado de la suma en el arreglo final.
        ByteBuffer buffer = ByteBuffer.wrap(new byte[8]);
        buffer.putLong(cs);

        bytes[bytes.length - 4] = buffer.array()[7];
        bytes[bytes.length - 3] = buffer.array()[6];
        bytes[bytes.length - 2] = buffer.array()[5];
        bytes[bytes.length - 1] = 0x0;

        if(bytes.length == 320){
            // Se restaura la cabecera respaldada al arreglo final.
            for(int i = 0; i<16; i++){
                bytes[i] = headerBackup[i];
            }
        }

        return bytes;
    }

    private static byte[] encrypt(String key, byte[] value) {
        try {
            byte[] initVector = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
            IvParameterSpec iv = new IvParameterSpec(initVector);
            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);

            byte[] encrypted = cipher.doFinal(value);

            return Base64.encode(encrypted,Base64.NO_WRAP);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

    private static byte[] decrypt(byte[] hash,  byte[] encrypted) {
        try {
            byte[] initVector = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
            IvParameterSpec iv = new IvParameterSpec(initVector);
            SecretKeySpec skeySpec = new SecretKeySpec(hash, "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);

            byte[] original = cipher.doFinal(encrypted);

            return original;

        } catch (Exception ex) {
            return(null);
        }
    }

    private void forgetConnection(){

        WifiManager wifiManager = (WifiManager) getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);

        List<WifiConfiguration> res = wifiManager.getConfiguredNetworks();
        if(res != null){
            for( WifiConfiguration w : res ) {
                if(w.SSID.length() > 5){
                    if(w.SSID.equals(this.sra.selectedSSID)
                            || String.format("\"%s\"", w.SSID)
                            .equals(this.sra.selectedSSID)
                            || w.SSID.equals(String.format("\"%s\"",
                            this.sra.selectedSSID))
                            || String.format("\"%s\"", w.SSID)
                            .equals(String.format("\"%s\"", this.sra.selectedSSID))){
                        wifiManager.removeNetwork(w.networkId);
                        wifiManager.saveConfiguration();
                    }
                }
            }
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 666){
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                startScan(null);
            }
        }
    }

}
