/**
 *  Última modificación: 18/Oct/2018
 *  Por: Ing. Edgardo Lira Hurtado
 *
 *  Clase "ClientRequestActivity"
 *  Autor: Ing. Edgardo Lira Hurtado
 *
 *  Actividad asincrona, se ejecuta en un hilo independiente.
 *
 *  Esta clase envía un comando al hardware a través de un socket predefinido;
 *  espera la respuesta y la coloca en la variable "data", la cual puede obtenerse
 *  con el metodo "getData()". Tanto el comando a enviar como la respuesta obtenida
 *  se manejan en variables de tipo "String".
 *
 *  ¡ATENCIÓN!:
 *  El comando que se pretende enviar debe estar en la variable "reqCode" antes
 *  de que inicie la ejecución de esta clase con el comando "execute()"; para esto se
 *  proporciona el metodo "setReqCode()".
 *
 */

package mx.eltec.smartheattester;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

public class clientRequestActivity extends AsyncTask{

    private Socket socket = new Socket();
    Context context = null;
    boolean oldPortNeeded = false;
    private static final int SERVERPORT = 8080;
    private static final int OLD_SERVERPORT = 80;
    private static final String SERVER_IP = "192.168.4.20";

    String data = "-vacio-";
    byte[] reqCode;

    private boolean abort = false;

    @Override
    protected Object doInBackground(Object[] params) {

        //Log.i(">>>>>","CLIENT R. A. doInBackground");
        StringBuilder total = new StringBuilder();
        String line;
        Boolean socketOkFlag = false;
        int attemptCount = 0;

        while(!socketOkFlag && !this.abort){

            try{
                socket = null;
                socketOkFlag = true;
                attemptCount++;
                InetAddress serverAddr = InetAddress.getByName(SERVER_IP);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){

                    ConnectivityManager connectivityManager = (ConnectivityManager)
                            context.getSystemService(Context.CONNECTIVITY_SERVICE);
                    Network[] nets = connectivityManager.getAllNetworks();
                    //Log.i(">>>>>","NETs COUNT: " + String.valueOf(nets.length));
                    for(Network net : nets){
                        //Log.i(">>>>>","NET: " + connectivityManager.getNetworkInfo(net).getTypeName());
                        if(connectivityManager.getNetworkInfo(net).getType() == ConnectivityManager.TYPE_WIFI){
                            //connectivityManager.bindProcessToNetwork(net);
                            try {

                                socket = net.getSocketFactory().createSocket(serverAddr, oldPortNeeded ? OLD_SERVERPORT : SERVERPORT);
                                socket.setSoTimeout(15000);
                                //net.bindSocket(socket);
                                Log.i(">>>>>","BIND NET OK!");

                            } catch (IOException e) {
                                e.printStackTrace();
                                Log.i(">>>>>","BIND NET ERROR: " + e.toString());
                                if(e.toString().contains("Connection refused")) {
                                    Log.i(">>>>>","Using Old Port? " + String.valueOf(this.oldPortNeeded));
                                    return "WrongPort?";
                                }
                            }
                            //Log.i(">>>>>","bindSocket!!!");
                            break;
                        }
                    }
                }

                if(socket == null){
                    socket = new Socket(serverAddr, oldPortNeeded ? OLD_SERVERPORT : SERVERPORT);                                           //Inicializa el Socket y establece la conexión con el hardware.
                    socket.setSoTimeout(15000);
                }

            }catch (IOException e0){

                if(attemptCount < 30){
                    socketOkFlag = false;
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }else{
                    return "";
                }
            }
        }

        int attemptCount2 = 0;
        Boolean okFlag = false;
        while(!okFlag && !this.abort){
            try{

                okFlag = true;
                attemptCount2++;
                BufferedReader input = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));                               //Buffer para recibir comandos desde el hardware.

                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.write(reqCode,0,reqCode.length);

                int c = 0;
                if(socket.isConnected() && socket.isBound()){
                    while(!input.ready() && c < 500 && !abort){
                        if(this.abort) break;
                        Thread.sleep(10);
                        c++;
                    }
                }

                while ((line = input.readLine()) != null && !abort) {                                 //Mientras existe información entrante en el buffer, esta se recibe en un "String".
                    if(this.abort) break;
                    total.append(line);
                }

                if(this.abort) return data;

                data = total.toString();

                out.close();
                input.close();
                socket.close();

            }catch (IOException e0){
                return "";
            } catch (InterruptedException e) {
                e.printStackTrace();
                return "timeout";
            }
        }
        return data;
    }

    /*private static String getIpAddress(WifiManager wifiManager) {
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();
        return String.format("%d.%d.%d.%d", (ipAddress & 0xff),(ipAddress >> 8 & 0xff),
                (ipAddress >> 16 & 0xff),(ipAddress >> 24 & 0xff));
    }*/

    public void setReqCode(byte[] reqCode) {
        this.reqCode = reqCode;
    }

    @Override
    protected void onCancelled() {
        try {
            if(this.socket != null) this.socket.close();
            this.abort = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.onCancelled();
    }
}