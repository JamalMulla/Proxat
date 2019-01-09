package com.jmulla.proxat

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Color
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AlertDialog
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.widget.LinearLayout
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.Payload
import java.nio.charset.Charset
import java.util.*






class MainActivity : ConnectionsActivity() {

    private val SERVICE_ID = "com.jmulla.proximitychat.amorphousblob"
    /**
     * The state of the app. As the app changes states, the UI will update and advertising/discovery
     * will start/stop.
     */
    private var mState = State.UNKNOWN
    private var btn_send: Button? = null
    private var tv_connected : TextView? = null
    private var et_texttosend: EditText? = null
    private var ll_received: LinearLayout? = null
    private var scrollview: ScrollView? = null
    /** A random UID used as this device's endpoint name.  */
    private var mName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mName = getRandomName()
        btn_send = findViewById(R.id.btn_send)
        //tv_received = findViewById(R.id.tv_received)
        et_texttosend = findViewById(R.id.et_texttosend)
        ll_received = findViewById(R.id.ll_messages_received)
        tv_connected = findViewById(R.id.tv_connected_to)
        scrollview = findViewById(R.id.scrollMessages)
    }


    override fun onStart() {
        super.onStart()
        if (isMarshmallowPlus()) {
            //locationStatusCheck()
            displayLocationSettingsRequest(context = baseContext)
        }
        btn_send?.setOnClickListener {
            val initText: String = et_texttosend?.text.toString()
            if (!initText.isEmpty()) {
                if (getState() == State.CONNECTED) {
                    //Toast.makeText(applicationContext, initText, Toast.LENGTH_SHORT).show()
                    val fromBytes = Payload.fromBytes(initText.toByteArray(Charset.defaultCharset()))
                    send(fromBytes)
                    et_texttosend?.setText("")
                    val message = TextView(applicationContext)
                    message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18.0f)
                    val llp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    llp.setMargins(4, 4, 4, 4)
                    llp.gravity = Gravity.END
                    message.layoutParams = llp
                    message.setTextColor(Color.parseColor("#ffffff"))
                    message.background = resources.getDrawable(R.drawable.rounded_corners, theme)
                    message.text = initText
                    message.setTextIsSelectable(true)
                    ll_received?.addView(message)
                    et_texttosend?.requestFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)
                    scrollview?.fullScroll(View.FOCUS_DOWN)
                    //scrollview?.post({ scrollview?.fullScroll(View.FOCUS_DOWN) })
                } else {
                    Toast.makeText(applicationContext, "Not connected", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(applicationContext, "Empty", Toast.LENGTH_SHORT).show()
            }
        }

        scrollview?.post { scrollview?.fullScroll(View.FOCUS_DOWN) }

    }

    override fun onStop() {
        setState(State.UNKNOWN)
        super.onStop()
    }

    private fun setState(state: State) {
        if (mState == state) {
            return
        }

        val oldState = mState
        mState = state
        onStateChanged(oldState, state)
    }

    override fun onBackPressed() {
/*        if (getState() == State.CONNECTED) {
            setState(State.SEARCHING)
            return
        }*/
        super.onBackPressed()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> {
                //Toast.makeText(this, "ADD!", Toast.LENGTH_SHORT).show();
                val i = Intent(this, MyPreferencesActivity::class.java)
                startActivity(i)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }
    private fun onStateChanged(oldState: State, newState: State) {

        // Update Nearby Connections to the new state.
        when (newState) {
            State.SEARCHING -> {
                disconnectFromAllEndpoints()
                startDiscovering()
                startAdvertising()
                tv_connected?.text = "Searching"
            }
            State.CONNECTED -> {
                tv_connected?.text = "Connected"
                //stopDiscovering()
                //stopAdvertising()
            }
            else -> {
                tv_connected?.text = "Not connected"
            }
        }

        // Update the UI.
        when (oldState) {
            State.UNKNOWN -> {

            }

            State.SEARCHING ->
                when (newState) {
                    State.CONNECTED -> {
                        tv_connected?.text = "Connected to 1 device"
                    }
                    else -> {
                    }
                }// no-op
            State.CONNECTED ->
                when (newState) {
                    State.CONNECTED ->{
                        updateNumberConnected()
                    }
                    else -> {

                    }
                }

        }
    }

    fun updateNumberConnected(){
        val numConnected : Int = getDiscoveredEndpoints().size
        if (numConnected == 1) tv_connected?.text = "Connected to 1 device" else tv_connected?.text = "Connected to " + numConnected + " devices"
    }
    override fun onConnected(bundle: Bundle?) {
        super.onConnected(bundle)
        setState(State.SEARCHING)
    }

    /** We were disconnected! Halt everything!  */
    override fun onConnectionSuspended(reason: Int) {
        super.onConnectionSuspended(reason)
        setState(State.UNKNOWN)
    }

    override fun onEndpointDiscovered(endpoint: Endpoint) {
        // We found an advertiser!
        connectToEndpoint(endpoint)
    }

    override fun onReceive(endpoint: Endpoint?, payload: Payload) {
        if (payload.type == Payload.Type.BYTES) {
            val message: TextView = TextView(applicationContext)
            message.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18.0f)
            message.setBackgroundResource(R.drawable.rounded_corners)
            val llp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            llp.setMargins(4, 4, 4, 4)
            llp.gravity = Gravity.START
            message.layoutParams = llp
            message.setTextColor(Color.parseColor("#ffffff"))
            message.background = resources.getDrawable(R.drawable.rounded_corners_b, theme)
            //message.setBackgroundColor(ContextCompat.getColor(applicationContext, R.color.colorPrimaryDark))
            message.text = payload.asBytes()?.toString(Charset.defaultCharset())
            message.setTextIsSelectable(true)
            ll_received?.addView(message)
            et_texttosend?.requestFocus()
            scrollview?.fullScroll(View.FOCUS_DOWN)
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)
            scrollview?.post { scrollview?.fullScroll(View.FOCUS_DOWN) }
            //tv_received?.text = payload.asBytes()?.toString(Charset.defaultCharset())
        }
    }


    override fun onConnectionInitiated(endpoint: Endpoint, connectionInfo: ConnectionInfo) {
        // A connection to another device has been initiated! We'll use the auth token, which is the
        // same on both devices, to pick a color to use when we're connected. This way, users can
        // visually see which device they connected with.

        // We accept the connection immediately.
        acceptConnection(endpoint)
    }

    override fun getDiscoveredEndpoints(): Set<Endpoint> {
        return super.getDiscoveredEndpoints()
    }

    override fun onEndpointConnected(endpoint: Endpoint?) {
        Toast.makeText(this, "Connected to ${endpoint?.name}", Toast.LENGTH_SHORT).show()
        setState(State.CONNECTED)
    }

    override fun onEndpointDisconnected(endpoint: Endpoint?) {
        Toast.makeText(this, "Disconnected from ${endpoint?.name}", Toast.LENGTH_SHORT).show()
        setState(State.SEARCHING)
    }

    fun locationStatusCheck() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps()
        }
    }

    private fun displayLocationSettingsRequest(context: Context) {
        val googleApiClient = GoogleApiClient.Builder(context)
                .addApi(LocationServices.API).build()
        googleApiClient.connect()

        val locationRequest = LocationRequest.create()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.interval = 10000
        locationRequest.fastestInterval = 10000 / 2

        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        builder.setAlwaysShow(true)

        val Endresult = LocationServices.SettingsApi.checkLocationSettings(googleApiClient, builder.build())
        Endresult.setResultCallback { result ->
            val status = result.status
            when (status.statusCode) {
                LocationSettingsStatusCodes.SUCCESS -> {
                }
                LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {

                    try {
                        // Show the dialog by calling startResolutionForResult(), and check the result
                        // in onActivityResult().
                        status.startResolutionForResult(this@MainActivity, 1)
                    } catch (e: IntentSender.SendIntentException) {
                        //Log.i(FragmentActivity.TAG, "PendingIntent unable to execute request.")
                    }

                }
                LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                } /*Log.i(FragmentActivity.TAG, "Location settings are inadequate, and cannot be fixed here. Dialog not created.")*/
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == 1) {
            recreate()
            Toast.makeText(applicationContext, "Recreated", Toast.LENGTH_SHORT).show()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun buildAlertMessageNoGps() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("GPS is needed for this application to function")
                .setCancelable(false)
                .setPositiveButton("Enable") { dialog, id -> startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                .setNegativeButton("Exit") { dialog, id ->
                    dialog.cancel()
                    finish()
                }
        val alert = builder.create()
        alert.show()
    }

    private fun getRandomName(): String {
        return UUID.randomUUID().toString()
    }

    override fun getName(): String {
        val SP = PreferenceManager.getDefaultSharedPreferences(baseContext)
        val strUserName = SP.getString("username", getRandomName())
        return strUserName
    }

    private fun getState(): State {
        return mState
    }

    /** {@see ConnectionsActivity#getServiceId()}  */
    override fun getServiceId(): String {
        return SERVICE_ID
    }

    private fun isMarshmallowPlus(): Boolean {
        return android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.M
    }

    enum class State {
        UNKNOWN,
        SEARCHING,
        CONNECTED
    }

}