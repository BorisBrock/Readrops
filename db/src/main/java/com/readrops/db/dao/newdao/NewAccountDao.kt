package com.readrops.db.dao.newdao

import androidx.room.Dao
import androidx.room.Query
import com.readrops.db.entities.account.Account
import kotlinx.coroutines.flow.Flow

@Dao
interface NewAccountDao : NewBaseDao<Account> {

    @Query("Select Count(*) From Account")
    suspend fun selectAccountCount(): Int

    @Query("Select * From Account Where current_account = 1")
    fun selectCurrentAccount(): Flow<Account?>

    @Query("Delete From Account")
    suspend fun deleteAllAccounts()
}