package com.jmulla.proxat

import android.Manifest

import android.content.Context
import android.content.pm.PackageManager

import android.os.Bundle
import androidx.annotation.CallSuper

import androidx.core.app.ActivityCompat

import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity

import android.widget.Toast
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient

import com.google.android.gms.common.api.Status
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.util.*

/**
 * Created by Jamal on 06/08/2017.
 */
open class ConnectionsActivity : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {


    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.ACCESS_COARSE_LOCATION)
    private val STRATEGY = Strategy.P2P_CLUSTER


    private var mGoogleApiClient: GoogleApiClient? = null


    /** The devices we've discovered near us.  */
    private val mDiscoveredEndpoints = HashMap<String, Endpoint>()


    /**
     * The devices we have pending connections to. They will stay pending until we call accepted or rejected
     */
    private val mPendingConnections = HashMap<String, Endpoint>()

    /**
     * The devices we are currently connected to
     */
    private val mEstablishedConnections = HashMap<String, Endpoint>()

    /**
     * True if we are asking a discovered device to connect to us. While we ask, we cannot ask another
     * device.
     */
    private var mIsConnecting = false

    /** True if we are discovering.  */
    private var mIsDiscovering = false

    /** True if we are advertising.  */
    private var mIsAdvertising = false


    /** Callbacks for connections to other devices.  */
    private val mConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Toast.makeText(applicationContext, "onConnectionInitiated with $endpointId", Toast.LENGTH_LONG).show()
/*            logD(String.format(
                            "onConnectionInitiated(endpointId=%s, endpointName=%s)",
                            endpointId, connectionInfo.endpointName))*/
            val endpoint = Endpoint(endpointId, connectionInfo.endpointName)
            mPendingConnections.put(endpointId, endpoint)
            this@ConnectionsActivity.onConnectionInitiated(endpoint, connectionInfo)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
/*            logD(String.format("onConnectionResponse(endpointId=%s, result=%s)", endpointId, result))*/
            Toast.makeText(applicationContext, "onConnectionResponse: $result", Toast.LENGTH_LONG).show()
            // We're no longer connecting
            mIsConnecting = false

            if (!result.status.isSuccess) {
/*                logW(String.format(
                                "Connection failed. Received status %s.",
                                ConnectionsActivity.toString(result.status)))*/
                Toast.makeText(applicationContext, "onConnectionFailed: ${result.status}", Toast.LENGTH_LONG).show()
                onConnectionFailed(mPendingConnections.remove(endpointId))
                return
            }
            connectedToEndpoint(mPendingConnections.remove(endpointId)!!)
        }

        override fun onDisconnected(endpointId: String) {
            if (!mEstablishedConnections.containsKey(endpointId)) {
                //logW("Unexpected disconnection from endpoint " + endpointId)
                Toast.makeText(applicationContext, "Unexpected disconnection from endpoint $endpointId", Toast.LENGTH_LONG).show()
                return
            }
            disconnectedFromEndpoint(mEstablishedConnections[endpointId])
        }
    }
    /** Callbacks for payloads (bytes of data) sent from another device to us.  */
    private val mPayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            //logD(String.format("onPayloadReceived(endpointId=%s, payload=%s)", endpointId, payload))
            Toast.makeText(applicationContext, "onPayloadReceived $endpointId $payload", Toast.LENGTH_LONG).show()
            onReceive(mEstablishedConnections[endpointId], payload)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
/*                logD(String.format(
                                "onPayloadTransferUpdate(endpointId=%s, update=%s)", endpointId, update))*/
        }
    }

    private fun resetState() {
        mDiscoveredEndpoints.clear()
        mPendingConnections.clear()
        mEstablishedConnections.clear()
        mIsConnecting = false
        mIsDiscovering = false
        mIsAdvertising = false
    }

    private fun createGoogleApiClient() {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = GoogleApiClient.Builder(this@ConnectionsActivity)
                    .addApi(Nearby.CONNECTIONS_API)
                    .addConnectionCallbacks(this)
                    .enableAutoManage(this, this)
                    .build()
        }
    }


    /**
     * Our Activity has just been made visible to the user. Our GoogleApiClient will start connecting
     * after super.onStart() is called.
     */


    override fun onStart() {
        if (hasPermissions(this, getRequiredPermissions())) {
            createGoogleApiClient()
            Toast.makeText(applicationContext, "Created google api client", Toast.LENGTH_LONG).show()
        } else {
            ActivityCompat.requestPermissions(this, getRequiredPermissions(), 1)
        }
        super.onStart()
    }

    override fun onConnected(bundle: Bundle?) {
        Toast.makeText(applicationContext, "Connected to google api client", Toast.LENGTH_LONG).show()
    }

    override fun onConnectionSuspended(reason: Int) {
        resetState()
        //temporarily disconnected
    }

    override fun onConnectionFailed(p0: ConnectionResult) {
        Toast.makeText(applicationContext, "Connection to google api client failed", Toast.LENGTH_LONG).show()
    }


    /** The user has accepted (or denied) our permission request.  */
    @CallSuper
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == 1) {
            for (grantResult in grantResults) {
                if (grantResult == PackageManager.PERMISSION_DENIED) {
                    Toast.makeText(this, "Did not receive the necessary permissions", Toast.LENGTH_LONG).show()
                    finish()
                    return
                }
            }
            recreate()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


    /**
     * Sets the device to advertising mode. It will broadcast to other devices in discovery mode.
     * Either [.onAdvertisingStarted] or [.onAdvertisingFailed] will be called once
     * we've found out if we successfully entered this mode.
     */
    protected fun startAdvertising() {
        mIsAdvertising = true
        Nearby.Connections.startAdvertising(
                mGoogleApiClient,
                getName(),
                getServiceId(),
                mConnectionLifecycleCallback,
                AdvertisingOptions(STRATEGY)).setResultCallback { result ->
            if (result.status.isSuccess) {
                Toast.makeText(applicationContext, "Now advertising endpoint ${result.localEndpointName}", Toast.LENGTH_LONG).show()
                //logV("Now advertising endpoint " + result.localEndpointName)
                onAdvertisingStarted()
            } else {
                mIsAdvertising = false
                Toast.makeText(applicationContext, "Advertising failed. Received status ${result.status}", Toast.LENGTH_LONG).show()
                /*logW(String.format(
                                "Advertising failed. Received status %s.",
                                ConnectionsActivity.toString(result.status)))*/
                onAdvertisingFailed()
            }
        }
    }

    /** Stops advertising.  */
    protected fun stopAdvertising() {
        mIsAdvertising = false
        Nearby.Connections.stopAdvertising(mGoogleApiClient)
    }

    /** @return True if currently advertising.
     */
    protected fun isAdvertising(): Boolean {
        return mIsAdvertising
    }

    /** Advertising has successfully started. Override this method to act on the event.  */
    private fun onAdvertisingStarted() {}

    /** Advertising has failed to start. Override this method to act on the event.  */
    private fun onAdvertisingFailed() {}

    /**
     * A pending connection with a remote endpoint has been created. Use [ConnectionInfo] for
     * metadata about the connection (like incoming vs outgoing, or the authentication token). If we
     * want to continue with the connection, call [.acceptConnection]. Otherwise, call
     * [.rejectConnection].
     */
    protected open fun onConnectionInitiated(endpoint: Endpoint, connectionInfo: ConnectionInfo) {}

    /** Accepts a connection request.  */
    protected fun acceptConnection(endpoint: Endpoint) {
        Nearby.Connections.acceptConnection(mGoogleApiClient, endpoint.id, mPayloadCallback)
                .setResultCallback { status ->
                    if (!status.isSuccess) {
                        Toast.makeText(applicationContext, "acceptConnectionFailed $status", Toast.LENGTH_LONG).show()
/*                        logW(String.format(
                                        "acceptConnection failed. %s", ConnectionsActivity.toString(status)))*/
                    }
                }
    }

    /** Rejects a connection request.  */
    protected fun rejectConnection(endpoint: Endpoint) {
        Nearby.Connections.rejectConnection(mGoogleApiClient, endpoint.id)
                .setResultCallback { status ->
                    if (!status.isSuccess) {
                        Toast.makeText(applicationContext, "rejectConnectionFailed $status", Toast.LENGTH_LONG).show()
/*                        logW(String.format(
                                        "rejectConnection failed. %s", ConnectionsActivity.toString(status)))*/
                    }
                }
    }

    /**
     * Sets the device to discovery mode. It will now listen for devices in advertising mode. Either
     * [.onDiscoveryStarted] ()} or [.onDiscoveryFailed] ()} will be called once we've
     * found out if we successfully entered this mode.
     */
    protected fun startDiscovering() {
        mIsDiscovering = true
        mDiscoveredEndpoints.clear()
        Nearby.Connections.startDiscovery(
                mGoogleApiClient,
                getServiceId(),
                object : EndpointDiscoveryCallback() {
                    override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                        Toast.makeText(applicationContext, "endpoint found ${info.endpointName}", Toast.LENGTH_LONG).show()
/*                        logD(String.format(
                                        "onEndpointFound(endpointId=%s, serviceId=%s, endpointName=%s)",
                                        endpointId, info.serviceId, info.endpointName))*/

                        if (getServiceId() == info.serviceId) {
                            Toast.makeText(applicationContext, "Service ids are same", Toast.LENGTH_LONG).show()
                            val endpoint = Endpoint(endpointId, info.endpointName)
                            mDiscoveredEndpoints.put(endpointId, endpoint)
                            onEndpointDiscovered(endpoint)
                        }
                    }

                    override fun onEndpointLost(endpointId: String) {
                        Toast.makeText(applicationContext, "endpoint lost $endpointId", Toast.LENGTH_LONG).show()
/*                        logD(String.format("onEndpointLost(endpointId=%s)", endpointId))*/
                    }
                },
                DiscoveryOptions(STRATEGY))
                .setResultCallback { status ->
                    if (status.isSuccess) {
                        onDiscoveryStarted()
                    } else {
                        mIsDiscovering = false
                        Toast.makeText(applicationContext, "Discovering failed. Received status $status", Toast.LENGTH_LONG).show()
/*                        logW(String.format(
                                        "Discovering failed. Received status %s.",
                                        ConnectionsActivity.toString(status)))*/
                        onDiscoveryFailed()
                    }
                }
    }

    /** Stops discovery.  */
    protected fun stopDiscovering() {
        mIsDiscovering = false
        Nearby.Connections.stopDiscovery(mGoogleApiClient)
    }

    /** @return True if currently discovering.
     */
    protected fun isDiscovering(): Boolean {
        return mIsDiscovering
    }

    /** Discovery has successfully started. Override this method to act on the event.  */
    protected fun onDiscoveryStarted() {}

    /** Discovery has failed to start. Override this method to act on the event.  */
    protected fun onDiscoveryFailed() {}

    /**
     * A remote endpoint has been discovered. Override this method to act on the event. To connect to
     * the device, call [.connectToEndpoint].
     */
    protected open fun onEndpointDiscovered(endpoint: Endpoint) {}

    protected fun disconnect(endpoint: Endpoint) {
        Nearby.Connections.disconnectFromEndpoint(mGoogleApiClient, endpoint.id)
        mEstablishedConnections.remove(endpoint.id)
    }

    protected fun disconnectFromAllEndpoints() {
        for (endpoint in mEstablishedConnections.values) {
            Nearby.Connections.disconnectFromEndpoint(mGoogleApiClient, endpoint.id)
        }
        mEstablishedConnections.clear()
    }

    /** Sends a connection request to the endpoint.  */
    protected fun connectToEndpoint(endpoint: Endpoint) {
        // If we already sent out a connection request, wait for it to return
        // before we do anything else. P2P_STAR only allows 1 outgoing connection.
        if (mIsConnecting) {
            //logW("Already connecting, so ignoring this endpoint: " + endpoint)
            return
        }

        //logV("Sending a connection request to endpoint " + endpoint)
        // Mark ourselves as connecting so we don't connect multiple times
        mIsConnecting = true

        // Ask to connect
        Nearby.Connections.requestConnection(
                mGoogleApiClient, getName(), endpoint.id, mConnectionLifecycleCallback)
                .setResultCallback { status ->
                    if (!status.isSuccess) {
                        Toast.makeText(applicationContext, "requestConnection failed $status", Toast.LENGTH_LONG).show()
/*                        logW(String.format(
                                        "requestConnection failed. %s", ConnectionsActivity.toString(status)))*/
                        mIsConnecting = false
                        onConnectionFailed(endpoint)
                    }
                }
    }

    /** True if we're currently attempting to connect to another device.  */
    protected fun isConnecting(): Boolean {
        return mIsConnecting
    }

    private fun connectedToEndpoint(endpoint: Endpoint?) {
        Toast.makeText(applicationContext, "connectedToEndpoint $endpoint", Toast.LENGTH_LONG).show()
        //logD(String.format("connectedToEndpoint(endpoint=%s)", endpoint))
        if (endpoint != null) {
            mEstablishedConnections.put(endpoint.id, endpoint)
        }
        onEndpointConnected(endpoint)
    }

    private fun disconnectedFromEndpoint(endpoint: Endpoint?) {
        Toast.makeText(applicationContext, "disconnectedFromEndpoint $endpoint", Toast.LENGTH_LONG).show()
        //logD(String.format("disconnectedFromEndpoint(endpoint=%s)", endpoint))
        mEstablishedConnections.remove(endpoint?.id)
        onEndpointDisconnected(endpoint)
    }

    /** A connection with this endpoint has failed. Override this method to act on the event.  */
    protected open fun onConnectionFailed(endpoint: Endpoint?) {}

    /** Someone has connected to us. Override this method to act on the event.  */
    protected open fun onEndpointConnected(endpoint: Endpoint?) {}

    /** Someone has disconnected. Override this method to act on the event.  */
    protected open fun onEndpointDisconnected(endpoint: Endpoint?) {}

    /** @return A list of currently connected endpoints.
     */
    protected open fun getDiscoveredEndpoints(): Set<Endpoint> {
        val endpoints = HashSet<Endpoint>()
        endpoints.addAll(mDiscoveredEndpoints.values)
        return endpoints
    }


    /** @return A list of currently connected endpoints.
     */
    protected fun getConnectedEndpoints(): Set<Endpoint> {
        val endpoints = HashSet<Endpoint>()
        endpoints.addAll(mEstablishedConnections.values)
        return endpoints
    }


    /**
     * Sends a [Payload] to all currently connected endpoints.
     *
     * @param payload The data you want to send.
     */
    protected fun send(payload: Payload) {
        send(payload, mEstablishedConnections.keys)
    }

    private fun send(payload: Payload, endpoints: Set<String>) {
        Nearby.Connections.sendPayload(mGoogleApiClient, ArrayList(endpoints), payload)
                .setResultCallback { status ->
                    if (!status.isSuccess) {
                        Toast.makeText(applicationContext, "send failed $status", Toast.LENGTH_LONG).show()
/*                        logW(
                                String.format(
                                        "sendUnreliablePayload failed. %s",
                                        ConnectionsActivity.toString(status)))*/
                    }
                }
    }

    /**
     * Someone connected to us has sent us data. Override this method to act on the event.
     *
     * @param endpoint The sender.
     * @param payload The data.
     */
    protected open fun onReceive(endpoint: Endpoint?, payload: Payload) {}

    /**
     * An optional hook to pool any permissions the app needs with the permissions ConnectionsActivity
     * will request.
     *
     * @return All permissions required for the app to properly function.
     */
    protected fun getRequiredPermissions(): Array<String> {
        return REQUIRED_PERMISSIONS
    }

    /** @return The client's name. Visible to others when connecting.
     */
    protected open fun getName(): String {
        return ""
    }

    /**
     * @return The service id. This represents the action this connection is for. When discovering,
     * we'll verify that the advertiser has the same service id before we consider connecting to
     * them.
     */
    protected open fun getServiceId(): String {
        return ""
    }

    /**
     * Transforms a [Status] into a English-readable message for logging.
     *
     * @param status The current status
     * @return A readable String. eg. [404]File not found.
     */
    private fun toString(status: Status): String {
        return String.format(
                Locale.US,
                "[%d]%s",
                status.statusCode,
                if (status.statusMessage != null)
                    status.statusMessage
                else
                    ConnectionsStatusCodes.getStatusCodeString(status.statusCode))
    }

    /** @return True if the app was granted all the permissions. False otherwise.
     */
    private fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        return permissions.none { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
    }


    /** Represents a device we can talk to.  */
    class Endpoint constructor(val id: String, val name: String) {

        override fun equals(obj: Any?): Boolean {
            if (obj != null && obj is Endpoint) {
                val other = obj as Endpoint?
                return id == other?.id
            }
            return false
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }

        override fun toString(): String {
            return String.format("Endpoint{id=%s, name=%s}", id, name)
        }
    }


}