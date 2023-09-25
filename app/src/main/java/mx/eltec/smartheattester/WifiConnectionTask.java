/**
 *  Última modificación: 10/Ago/2016
 *  Por: Ing. Edgardo Lira Hurtado
 *
 *  Clase "WifiConnectionTask"
 *  Autor: Ing. Edgardo Lira Hurtado
 *
 *  Actividad asincrona para establecer una conexión wifi con el access point deseado.
 *
 */

package mx.eltec.smartheattester;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.util.Log;

import java.util.List;

public class WifiConnectionTask extends AsyncTask {

    public String ssid = "";
    public boolean ready;
    public Context context;
    private String key = "4396102199";

    @Override
    protected Object doInBackground(Object[] params) {

        // Configuración de la red wifi deseada:
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = ssid;
        //wifiConfig.preSharedKey = String.format("\"%s\"", key);
        //wifiConfig.status = WifiConfiguration.Status.DISABLED;
        //wifiConfig.priority = Integer.MAX_VALUE;

        // Configuración extra del wifi:
        wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);

        wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
        wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);

        // Se obtiene el id de la red deseada (se reintenta si en necesario):
        final WifiManager wifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);

            // Se desconecta el wifi (en caso de que ya existiera una conexión):
        Log.d("readi:",""+ready);
        if (!ready) //Se verifica que si se logró conectar o ya estaba conectado al ssid, no se desconecte
            wifiManager.disconnect();

        List<WifiConfiguration> res = wifiManager.getConfiguredNetworks();
        if(res != null){
            for( WifiConfiguration w : res ) {
                wifiManager.disableNetwork(w.networkId);
                if(this.isCancelled()) break;
                //wifiManager.removeNetwork(w.networkId);
            }
        }

        int currentNetId = wifiManager.addNetwork(wifiConfig);
        //wifiManager.saveConfiguration();

        // Se habilita la red configurada y se intenta la conexión:
        wifiManager.enableNetwork(currentNetId, true);
        wifiManager.reconnect();

        return null;
    }
}