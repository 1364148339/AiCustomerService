package com.kevinluo.autoglm.network

import com.kevinluo.autoglm.network.model.DeviceRegisterReq
import com.kevinluo.autoglm.network.model.DeviceRegisterResp
import com.kevinluo.autoglm.network.model.EventReq
import com.kevinluo.autoglm.network.model.EventResp
import com.kevinluo.autoglm.network.model.HeartbeatReq
import com.kevinluo.autoglm.network.model.HeartbeatResp
import com.kevinluo.autoglm.network.model.TaskDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AiMacrodroidApi {

    @POST("api/devices/register")
    suspend fun registerDevice(
        @Body request: DeviceRegisterReq
    ): DeviceRegisterResp

    @POST("api/devices/heartbeat")
    suspend fun heartbeat(
        @Body request: HeartbeatReq
    ): HeartbeatResp

    @GET("api/devices/{deviceId}/tasks")
    suspend fun pollTasks(
        @Path("deviceId") deviceId: String
    ): List<TaskDto>

    @POST("api/devices/{deviceId}/events")
    suspend fun reportEvent(
        @Path("deviceId") deviceId: String,
        @Body request: EventReq
    ): EventResp
}
