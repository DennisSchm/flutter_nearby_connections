package com.nankai.flutter_nearby_connections

import android.app.Activity
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import io.flutter.plugin.common.MethodChannel

const val connecting = 1
const val connected = 2
const val notConnected = 3

class CallbackUtils constructor(private val channel: MethodChannel, private val activity: Activity) {
    private val TAG = "flutter_nearby_connections-CallbackUtils"
    private val devices = mutableListOf<DeviceJson>()
    private val gson = Gson()
    private fun deviceExists(deviceId: String) = devices.any { element -> element.deviceID == deviceId }
    private fun device(deviceId: String): DeviceJson? = devices.find { element -> element.deviceID == deviceId }
    fun updateStatus(deviceId: String, state: Int) {
        devices.find { element -> element.deviceID == deviceId }?.state = state
        val json = gson.toJson(devices)
        channel.invokeMethod(INVOKE_CHANGE_STATE_METHOD, json)
    }

    fun addDevice(device: DeviceJson) {
        if (deviceExists(device.deviceID)) {
            updateStatus(device.deviceID, device.state)
        } else {
            devices.add(device)
        }
        val json = gson.toJson(devices)
        channel.invokeMethod(INVOKE_CHANGE_STATE_METHOD, json)
    }

    fun removeDevice(deviceId: String) {
        devices.remove(device(deviceId))
        val json = gson.toJson(devices)
        channel.invokeMethod(INVOKE_CHANGE_STATE_METHOD, json)
    }

    val endpointDiscoveryCallback: EndpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String,
                                     discoveredEndpointInfo: DiscoveredEndpointInfo) {
            Log.d(TAG, "onEndpointFound $discoveredEndpointInfo")
            if(!deviceExists(endpointId)) {
                val data = DeviceJson(endpointId, discoveredEndpointInfo.endpointName, notConnected)
                addDevice(data)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "onEndpointLost $endpointId")
            if (deviceExists(endpointId)) {
                Nearby.getConnectionsClient(activity).disconnectFromEndpoint(endpointId)
            }
            removeDevice(endpointId)
        }
    }

    private val payloadCallback: PayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.d(TAG, "onPayloadReceived $endpointId")
            val args = mutableMapOf("deviceId" to endpointId, "message" to String(payload.asBytes()!!))
            channel.invokeMethod(INVOKE_MESSAGE_RECEIVE_METHOD, args)
        }

        override fun onPayloadTransferUpdate(endpointId: String,
                                             payloadTransferUpdate: PayloadTransferUpdate) {
            // required for files and streams
            Log.d(TAG, "onPayloadTransferUpdate $endpointId")
        }
    }

    val connectionLifecycleCallback: ConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "onConnectionInitiated $connectionInfo")
            val data = DeviceJson(endpointId, connectionInfo.endpointName, connecting)
            addDevice(data)
            Nearby.getConnectionsClient(activity).acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            Log.d(TAG, "onConnectionResult $endpointId")
            val data = if (result.status.isSuccess) {
             DeviceJson(endpointId,
                        if (device(endpointId)?.deviceName == null) "Null" else device(endpointId)?.deviceName!!,connected)
            }else{
                DeviceJson(endpointId,
                        if (device(endpointId)?.deviceName == null) "Null" else device(endpointId)?.deviceName!!, notConnected)
            }
            addDevice(data)
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "onDisconnected $endpointId")
            if (deviceExists(endpointId)) {
                updateStatus(endpointId, notConnected)
            } else {
                val data = DeviceJson(endpointId, if (device(endpointId)?.deviceName == null) "Null" else device(endpointId)?.deviceName!!, notConnected)
                addDevice(data)
            }
        }
    }
}
