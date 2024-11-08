package com.example.beaconble

import android.app.*
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import org.altbeacon.beacon.*
import org.altbeacon.bluetooth.BluetoothMedic

class BeaconReferenceApplication: Application() {

    var region = Region("all-beacons", null, null, null)
    // Region representa el criterio que se usa para busacar las balizas, como no se quiere buscar una UUID especifica los 3 útlimos campos son null

    override fun onCreate() {
        super.onCreate()

        val beaconManager = BeaconManager.getInstanceForApplication(this)
        // Beacon Manager configura la interaccion con las beacons y el start/stop de ranging/monitoring

        // Por defecto la biblioteca solo detecta AltBeacon si se quiere otro tipo de protocolo hay que añadir el layout
        //añadir iBeacons
        val iBeaconParser = BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24")
        beaconManager.getBeaconParsers().add(iBeaconParser)

        //añadir Eddystone UID
        val eddyStoneUIDParser = BeaconParser().setBeaconLayout("s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19")
        beaconManager.getBeaconParsers().add(eddyStoneUIDParser)

        //añadir Eddystone TLM
        val eddyStoneTLMParser = BeaconParser().setBeaconLayout("s:0-1=feaa,m:2-2=20,d:3-3,d:4-5,d:6-7,d:8-11,d:12-15")
        beaconManager.getBeaconParsers().add(eddyStoneTLMParser)

        //añadir Eddystone URL
        val eddyStoneURLParser = BeaconParser().setBeaconLayout("s:0-1=feaa,m:2-2=10,p:3-3:-41,i:4-20v")
        beaconManager.getBeaconParsers().add(eddyStoneURLParser)

        //añadir beacon Custom
        val customParser = BeaconParser().setBeaconLayout("m:0-1=0505")
        beaconManager.getBeaconParsers().add(customParser)

        BeaconManager.setDebug(true)

        //configurar escaneo
        setupBeaconScanning()

    }


    fun setupBeaconScanning() {
        val beaconManager = BeaconManager.getInstanceForApplication(this)

        // Empieza a escanear en la region definida
        beaconManager.startMonitoring(region)
        beaconManager.startRangingBeacons(region)

        // Establecen dos observadores Live Data para cambios en el estado de la región y la lista de beacons detectado
        val regionViewModel = BeaconManager.getInstanceForApplication(this).getRegionViewModel(region)
        // Se llamara al observador cuando la region cambie de estado dentro/fuera
        regionViewModel.regionState.observeForever( centralMonitoringObserver)
        // Se llamara al observador cuando se actualice la lista de de beacons (normalmente se actualiza cada 1 seg)
        regionViewModel.rangedBeacons.observeForever( centralRangingObserver)

    }

    //registra los cambios de si estas dentro o fuera de la region con la interfaz MonitorNotifier de la biblioteca
    // si estas dentro te envia una notificacion
    val centralMonitoringObserver = Observer<Int> { state ->
        if (state == MonitorNotifier.OUTSIDE) {
            Log.d(TAG, "outside beacon region: "+region)
        }
        else {
            Log.d(TAG, "inside beacon region: "+region)
            sendNotification()
        }
    }

    //recibe actualizaciones de la lista de beacon detectados y su info
    // hace un calculo del tiempo en millis que ha pasado desde la ultima actualizacion
    val centralRangingObserver = Observer<Collection<Beacon>> { beacons ->
        val rangeAgeMillis = System.currentTimeMillis() - (beacons.firstOrNull()?.lastCycleDetectionTimestamp ?: 0)
        if (rangeAgeMillis < 10000) {
            Log.d(MainActivity.TAG, "Ranged: ${beacons.count()} beacons")
            for (beacon: Beacon in beacons) {
                Log.d(TAG, "$beacon about ${beacon.distance} meters away")
            }
        }
        else {
            Log.d(MainActivity.TAG, "Ignoring stale ranged beacons from $rangeAgeMillis millis ago")
        }
    }

        //envia notificacion cuando se detecta un beacon en la region
    private fun sendNotification() {
        val builder = NotificationCompat.Builder(this, "beacon-ref-notification-id")
            .setContentTitle("Beacon Reference Application")
            .setContentText("A beacon is nearby.")
            .setSmallIcon(R.drawable.ic_launcher_background)

        //crea una pila de back para la Actividad que se lanza cuando el usuario toca la notificacion
        val stackBuilder = TaskStackBuilder.create(this)
        stackBuilder.addNextIntent(Intent(this, MainActivity::class.java))
        val resultPendingIntent = stackBuilder.getPendingIntent(
            0,
            PendingIntent.FLAG_UPDATE_CURRENT + PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(resultPendingIntent)
        val channel =  NotificationChannel("beacon-ref-notification-id",
            "My Notification Name", NotificationManager.IMPORTANCE_DEFAULT)
        channel.setDescription("My Notification Channel Description")
        val notificationManager =  getSystemService(
            Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel);
        builder.setChannelId(channel.getId());
        notificationManager.notify(1, builder.build())
    }

    companion object {
        val TAG = "BeaconReference"
    }

}