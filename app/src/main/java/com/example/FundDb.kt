package com.example

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "funds")
data class FundData(
    @PrimaryKey val schemeName: String,
    val rawSchemeName: String,
    val navValue: Double,
    val pctChange: Double,
    val pctChangeString: String,
    val navDate: String,
    val option: String,
    val fetchedAtTimestamp: Long,
    val previousDayNav: Double? = null,
    val globalHighestNav: Double? = null,
    val globalHighestNavDate: String? = null
)

@Entity(tableName = "investments")
data class FundInvestment(
    @PrimaryKey val schemeName: String,
    val investedAmount: Double,
    val unitsHeld: Double,
    val highestNav: Double = 0.0,
    val highestNavDate: String = ""
)

@Entity(tableName = "historical_navs", primaryKeys = ["schemeName", "dateStr"])
data class HistoricalNav(
    val schemeName: String,
    val dateStr: String,
    val navValue: Double
)


@Entity(tableName = "cas_transactions")
data class CasTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val schemeName: String,
    val date: String,
    val description: String,
    val amount: Double,
    val units: Double,
    val nav: Double,
    val balance: Double,
    val type: String // Buy, Sell, Switch In, Switch Out, etc.
)

@Dao
interface FundDao {
    @Query("SELECT * FROM funds ORDER BY schemeName ASC")
    fun getAllFunds(): Flow<List<FundData>>

    @Query("SELECT * FROM funds ORDER BY schemeName ASC")
    fun getFundsDirectSync(): List<FundData>

    @Query("SELECT * FROM funds")
    suspend fun getFundsDirect(): List<FundData>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFunds(funds: List<FundData>)

    @Query("DELETE FROM funds")
    suspend fun deleteAllFunds()

    @Query("DELETE FROM funds WHERE schemeName NOT IN (:allowedSchemes)")
    suspend fun deleteFundsNotIn(allowedSchemes: List<String>)

    @Query("DELETE FROM funds WHERE schemeName NOT IN (:allowedSchemes)")
    fun deleteFundsNotInSync(allowedSchemes: List<String>)

    @Query("UPDATE funds SET globalHighestNav = :nav, globalHighestNavDate = :date WHERE schemeName = :schemeName")
    suspend fun updateFundHighest(schemeName: String, nav: Double, date: String)

    @Query("SELECT * FROM investments")
    fun getAllInvestments(): Flow<List<FundInvestment>>

    @Query("SELECT * FROM investments")
    fun getAllInvestmentsDirectSync(): List<FundInvestment>

    @Query("SELECT * FROM investments WHERE schemeName = :schemeName LIMIT 1")
    suspend fun getInvestmentByScheme(schemeName: String): FundInvestment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvestment(investment: FundInvestment)

    @Query("DELETE FROM investments WHERE schemeName = :schemeName")
    suspend fun deleteInvestment(schemeName: String)

    @Query("SELECT * FROM historical_navs WHERE schemeName = :schemeName ORDER BY dateStr DESC")
    suspend fun getHistoricalNavs(schemeName: String): List<HistoricalNav>

    @Query("SELECT * FROM historical_navs")
    fun getAllHistoricalNavs(): Flow<List<HistoricalNav>>

    @Query("SELECT * FROM historical_navs")
    fun getAllHistoricalNavsDirectSync(): List<HistoricalNav>

    @Query("SELECT * FROM historical_navs")
    suspend fun getAllHistoricalNavsDirect(): List<HistoricalNav>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoricalNavs(navs: List<HistoricalNav>)
    
    @Query("DELETE FROM historical_navs")
    suspend fun deleteAllHistoricalNavs()

    @Query("DELETE FROM historical_navs WHERE schemeName NOT IN (:allowedSchemes)")
    suspend fun deleteHistoricalNavsNotIn(allowedSchemes: List<String>)

    @Query("SELECT * FROM cas_transactions WHERE schemeName = :schemeName ORDER BY date ASC")
    fun getTransactionsForScheme(schemeName: String): Flow<List<CasTransactionEntity>>

    @Query("SELECT * FROM cas_transactions WHERE schemeName = :schemeName ORDER BY date ASC")
    suspend fun getTransactionsForSchemeDirect(schemeName: String): List<CasTransactionEntity>

    @Query("SELECT * FROM cas_transactions ORDER BY date ASC")
    fun getAllTransactions(): Flow<List<CasTransactionEntity>>

    @Query("SELECT * FROM cas_transactions ORDER BY date ASC")
    fun getAllTransactionsDirectSync(): List<CasTransactionEntity>

    @Query("SELECT * FROM cas_transactions ORDER BY date ASC")
    suspend fun getAllTransactionsDirect(): List<CasTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<CasTransactionEntity>)

    @Query("DELETE FROM cas_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Int)

    @Query("DELETE FROM cas_transactions")
    suspend fun deleteAllTransactions()
}

@Database(entities = [FundData::class, FundInvestment::class, HistoricalNav::class, CasTransactionEntity::class], version = 10, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fundDao(): FundDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "qsif_nav_database"
                )
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
