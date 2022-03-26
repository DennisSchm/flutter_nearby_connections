package com.nankai.flutter_nearby_connections

import android.app.Activity
import android.util.Base64
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import io.flutter.plugin.common.MethodChannel

const val connecting = 1
const val connected = 2
const val notConnected = 3

class CallbackUtils constructor(
    private val channel: MethodChannel,
    private val activity: Activity
) {
    private val TAG = "FNC_CallbackUtils"
    private val devices = mutableListOf<DeviceJson>()
    private val gson = Gson()

    private fun deviceExists(deviceId: String) =
        devices.any { element -> element.deviceID == deviceId }

    private fun device(deviceId: String): DeviceJson? =
        devices.find { element -> element.deviceID == deviceId }

    fun updateStatus(deviceId: String, state: Int) {
        device(deviceId)?.state = state
    }

    fun addDevice(device: DeviceJson) {
        Log.d(TAG, "addDevice $device")
        if (deviceExists(device.deviceID)) {
            updateStatus(device.deviceID, device.state)
            if (device.token != null) {
                device(device.deviceID)?.token = device.token
            }
        } else {
            devices.add(device)
        }
        invokeChangeState()
    }

    fun removeDevice(deviceId: String) {
        Log.d(TAG, "removeDevice $deviceId")
        devices.remove(device(deviceId))
        invokeChangeState()
    }

    fun invokeChangeState() {
        val json = gson.toJson(devices)
        channel.invokeMethod(INVOKE_CHANGE_STATE_METHOD, json)
    }

    val endpointDiscoveryCallback: EndpointDiscoveryCallback =
        object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(
                endpointId: String,
                discoveredEndpointInfo: DiscoveredEndpointInfo
            ) {
                Log.d(TAG, "onEndpointFound $discoveredEndpointInfo")
                if (!deviceExists(endpointId)) {
                    val data = DeviceJson(
                        endpointId,
                        discoveredEndpointInfo.endpointName,
                        notConnected,
                        null
                    )
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
            val args =
                mutableMapOf("deviceId" to endpointId, "message" to String(payload.asBytes()!!))
            channel.invokeMethod(INVOKE_MESSAGE_RECEIVE_METHOD, args)
        }

        override fun onPayloadTransferUpdate(
            endpointId: String,
            payloadTransferUpdate: PayloadTransferUpdate
        ) {
            // required for files and streams
            Log.d(TAG, "onPayloadTransferUpdate $endpointId")
        }
    }

    val connectionLifecycleCallback: ConnectionLifecycleCallback =
        object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                Log.d(TAG, "onConnectionInitiated $connectionInfo")
                val data = DeviceJson(
                    endpointId,
                    connectionInfo.endpointName,
                    connecting,
                    Base64.encodeToString(
                        connectionInfo.getRawAuthenticationToken(),
                        Base64.DEFAULT
                    )
                )
                addDevice(data)
                Nearby.getConnectionsClient(activity).acceptConnection(endpointId, payloadCallback)
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                Log.d(TAG, "onConnectionResult $endpointId")
                val data = if (result.status.isSuccess) {
                    DeviceJson(
                        endpointId,
                        if (device(endpointId)?.deviceName == null) "Null" else device(endpointId)?.deviceName!!,
                        connected,
                        null
                    )
                } else {
                    DeviceJson(
                        endpointId,
                        if (device(endpointId)?.deviceName == null) "Null" else device(endpointId)?.deviceName!!,
                        notConnected,
                        null
                    )
                }
                addDevice(data)
            }

            override fun onDisconnected(endpointId: String) {
                Log.d(TAG, "onDisconnected $endpointId")
                if (deviceExists(endpointId)) {
                    updateStatus(endpointId, notConnected)
                    invokeChangeState()
                    device(endpointId)?.token = null
                } else {
                    val data = DeviceJson(
                        endpointId,
                        if (device(endpointId)?.deviceName == null) "Null" else device(endpointId)?.deviceName!!,
                        notConnected,
                        null
                    )
                    addDevice(data)
                }
            }
        }
}
