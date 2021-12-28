package com.readrops.api.services.fever

import com.readrops.api.services.SyncType
import com.readrops.api.services.fever.adapters.FeverAPIAdapter
import com.readrops.api.utils.ApiUtils
import com.readrops.db.entities.Item
import com.squareup.moshi.Moshi
import okhttp3.MultipartBody

class FeverDataSource(private val service: FeverService) {

    suspend fun login(login: String, password: String) {
        val credentials = ApiUtils.md5hash("$login:$password")

        val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("api_key", credentials)
                .build()

        val response = service.login(requestBody)

        val adapter = Moshi.Builder()
                .add(Boolean::class.java, FeverAPIAdapter())
                .build()
                .adapter(Boolean::class.java)

        adapter.fromJson(response.source())!!
    }

    suspend fun sync(syncType: SyncType, syncData: FeverSyncData, body: MultipartBody): FeverSyncResult {
        if (syncType == SyncType.INITIAL_SYNC) {
            val unreadIds = service.getUnreadItemsIds(body)
                    .reversed()
                    .subList(0, MAX_ITEMS_IDS)

            var lastId = unreadIds.first()
            val items = arrayListOf<Item>()
            repeat(INITIAL_SYNC_ITEMS_REQUESTS_COUNT) {
                val newItems = service.getItems(body, lastId, null)

                lastId = newItems.last().remoteId!!
                items += newItems
            }

            return FeverSyncResult(
                    feverFeeds = service.getFeeds(body),
                    folders = service.getFolders(body),
                    items = items,
                    unreadIds = unreadIds,
                    starredIds = service.getStarredItemsIds(body),
                    favicons = listOf(),
                    sinceId = unreadIds.first().toLong(),
            )
        } else {
            val items = arrayListOf<Item>()
            var sinceId = syncData.sinceId

            while (true) {
                val newItems = service.getItems(body, null, sinceId)

                if (newItems.isEmpty()) break
                sinceId = newItems.first().remoteId!!
                items += newItems
            }

            return FeverSyncResult(
                    feverFeeds = service.getFeeds(body),
                    folders = service.getFolders(body),
                    items = items,
                    unreadIds = service.getUnreadItemsIds(body),
                    starredIds = service.getStarredItemsIds(body),
                    favicons = listOf(),
                    sinceId = if (items.isNotEmpty()) items.first().remoteId!!.toLong() else sinceId.toLong(),
            )
        }
    }

    companion object {
        private const val MAX_ITEMS_IDS = 5000
        private const val INITIAL_SYNC_ITEMS_REQUESTS_COUNT = 10
    }
}