package com.readrops.app.compose.repositories

import com.readrops.api.localfeed.LocalRSSDataSource
import com.readrops.api.services.SyncResult
import com.readrops.db.Database
import com.readrops.db.entities.Feed
import com.readrops.db.entities.Item
import com.readrops.db.entities.account.Account
import org.jsoup.Jsoup

class LocalRSSRepository(
    private val dataSource: LocalRSSDataSource,
    database: Database,
    account: Account
) : BaseRepository(database, account) {

    override suspend fun login() { /* useless here */
    }

    override suspend fun synchronize(
        selectedFeeds: List<Feed>?,
        onUpdate: (Feed) -> Unit
    ): Pair<SyncResult, ErrorResult> {
        val errors = mutableMapOf<Feed, Exception>()
        val syncResult = SyncResult()

        val feeds = if (selectedFeeds.isNullOrEmpty()) {
            database.newFeedDao().selectFeeds(account.id)
        } else selectedFeeds

        for (feed in feeds) {
            try {
                val pair = dataSource.queryRSSResource(feed.url!!, null)

                pair?.let {
                    insertNewItems(it.second, feed)
                    syncResult.items = it.second
                }
            } catch (e: Exception) {
                errors[feed] = e
            }

        }

        return Pair(syncResult, ErrorResult(errors))
    }

    override suspend fun synchronize(): SyncResult = throw NotImplementedError("This method can't be called here")


    override suspend fun insertNewFeeds(urls: List<String>) {
        for (url in urls) {
            try {
                val result = dataSource.queryRSSResource(url, null)!!
                insertFeed(result.first)
            } catch (e: Exception) {
                throw e
            }
        }
    }

    private suspend fun insertNewItems(items: List<Item>, feed: Feed) {
        items.sortedWith(Item::compareTo) // TODO Check if ordering is useful in this situation
        val itemsToInsert = mutableListOf<Item>()

        for (item in items) {
            if (!database.itemDao().itemExists(item.guid!!, feed.accountId)) {
                if (item.description != null) {
                    item.cleanDescription = Jsoup.parse(item.description).text()
                }

                if (item.content != null) {
                    item.readTime = 0.0
                } else if (item.description != null) {
                    item.readTime = 0.0
                }

                item.feedId = feed.id
                itemsToInsert += item
            }
        }

        database.newItemDao().insert(itemsToInsert)
    }

    private suspend fun insertFeed(feed: Feed): Feed {
        require(!database.newFeedDao().feedExists(feed.url!!, account.id)) {
            "Feed already exists for account ${account.accountName}"
        }

        return feed.apply {
            accountId = account.id
            // we need empty headers to query the feed just after, without any 304 result
            etag = null
            lastModified = null

            id = database.newFeedDao().insert(this).toInt()
        }
    }
}