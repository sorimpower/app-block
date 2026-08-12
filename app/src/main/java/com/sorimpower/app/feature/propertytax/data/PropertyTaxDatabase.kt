package com.sorimpower.app.feature.propertytax.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "property_tax_properties", indices = [Index("status")])
data class PropertyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val propertyType: String,
    val address: String,
    val acquisitionDate: String,
    val acquisitionPrice: Long,
    val ownershipRatio: Double,
    val officialAssessedValue: Long?,
    val currentEstimatedValue: Long?,
    val actualAcquisitionTax: Long?,
    val brokerageFee: Long,
    val legalFee: Long,
    val renovationCost: Long,
    val otherNecessaryExpenses: Long,
    val residenceStartDate: String?,
    val residenceEndDate: String?,
    val status: String,
    val spouseOwnershipRatio: Double,
    val regulatedAreaAtAcquisition: Boolean?,
    val expectedCompletionDate: String?,
    val ownerBirthYear: Int?,
    val spouseBirthYear: Int?,
    val ownerBirthDate: String?,
    val spouseBirthDate: String?,
    val acquisitionContractDate: String?,
    val urbanAreaTaxApplicable: Boolean?,
    val annualRegionalResourceTax: Long?,
    val acquisitionRuralSpecialTax: Long?,
    val acquisitionHouseCountTreatment: String,
    val capitalGainsHouseCountTreatment: String,
    val comprehensiveTaxTreatment: String,
    val capitalGainsSurchargeTreatment: String,
    val acquisitionSurchargeRelief: String,
    val previousHomeDispositionDate: String?,
    val residenceRequirementExempt: Boolean,
    val jointComprehensiveTaxSpecialRequested: Boolean,
    val jointSpecialTaxpayer: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "property_tax_rule_versions")
data class TaxRuleVersionEntity(@PrimaryKey val id: String, val name: String, val effectiveFrom: String, val effectiveUntil: String?, val sourceUpdatedAt: String, val sourceReferencesJson: String, val status: String, val createdAt: Long)

@Entity(tableName = "property_tax_simulations", indices = [Index("propertyId"), Index("createdAt")])
data class SaleSimulationEntity(
    @PrimaryKey val id: String,
    val propertyId: String,
    val name: String,
    val expectedSaleDate: String,
    val expectedSalePrice: Long,
    val additionalNecessaryExpenses: Long,
    val portfolioHouseCountAtSale: Int,
    val regulatedAreaAtSale: Boolean?,
    val surchargeGraceEligible: Boolean,
    val postCompletionPresaleSpecialEligible: Boolean,
    val saleContractDate: String?,
    val depositReceived: Boolean,
    val landTransactionPermitRequired: Boolean?,
    val landTransactionPermitApplicationDate: String?,
    val landTransactionPermitApproved: Boolean,
    val extendedSurchargeGraceRegion: Boolean,
    val completedHomeMoveInDate: String?,
    val completedHomeResidenceEndDate: String?,
    val ownerBasicDeductionUsed: Long,
    val spouseBasicDeductionUsed: Long,
    val taxRuleVersionId: String,
    val totalEstimatedTax: Long,
    val nationalCapitalGainsTax: Long,
    val localIncomeTax: Long,
    val capitalGain: Long,
    val longTermDeduction: Long,
    val taxBase: Long,
    val confidence: String,
    val missingInputsJson: String,
    val appliedRulesJson: String,
    val calculationTraceJson: String,
    val createdAt: Long,
    val calculatedAt: Long,
)

@Entity(tableName = "property_tax_simulation_revisions", indices = [Index("simulationId")])
data class SimulationRevisionEntity(@PrimaryKey val id: String, val simulationId: String, val taxRuleVersionId: String, val totalEstimatedTax: Long, val resultJson: String, val calculatedAt: Long, val previousRevisionId: String?)

@Entity(tableName = "property_tax_scenarios")
data class PropertyTaxScenarioEntity(@PrimaryKey val id: String, val name: String, val taxRuleVersionId: String, val createdAt: Long)

@Entity(tableName = "property_tax_scenario_transactions", indices = [Index("scenarioId")])
data class ScenarioTransactionEntity(@PrimaryKey val id: String, val scenarioId: String, val sequence: Int, val type: String, val propertyId: String?, val transactionDate: String, val transactionPrice: Long, val propertyDraftJson: String?)

@Entity(tableName = "property_tax_ai_cache", indices = [Index("simulationId")])
data class PropertyTaxAiCacheEntity(@PrimaryKey val cacheKey: String, val simulationId: String, val model: String, val promptVersion: String, val resultJson: String, val createdAt: Long)

@Dao
interface PropertyTaxDao {
    @Query("SELECT * FROM property_tax_properties ORDER BY status, acquisitionDate") fun observeProperties(): Flow<List<PropertyEntity>>
    @Query("SELECT * FROM property_tax_simulations ORDER BY createdAt DESC") fun observeSimulations(): Flow<List<SaleSimulationEntity>>
    @Query("SELECT * FROM property_tax_simulation_revisions ORDER BY calculatedAt DESC") fun observeRevisions(): Flow<List<SimulationRevisionEntity>>
    @Query("SELECT * FROM property_tax_rule_versions WHERE status='ACTIVE' ORDER BY effectiveFrom DESC LIMIT 1") fun observeActiveRule(): Flow<TaxRuleVersionEntity?>
    @Query("SELECT * FROM property_tax_scenarios ORDER BY createdAt DESC") fun observeScenarios(): Flow<List<PropertyTaxScenarioEntity>>
    @Query("SELECT * FROM property_tax_scenario_transactions ORDER BY scenarioId, sequence") fun observeScenarioTransactions(): Flow<List<ScenarioTransactionEntity>>
    @Query("SELECT * FROM property_tax_properties WHERE id=:id LIMIT 1") suspend fun property(id: String): PropertyEntity?
    @Query("SELECT * FROM property_tax_properties WHERE status='OWNED'") suspend fun ownedProperties(): List<PropertyEntity>
    @Query("SELECT * FROM property_tax_simulations WHERE id=:id LIMIT 1") suspend fun simulation(id: String): SaleSimulationEntity?
    @Query("SELECT * FROM property_tax_simulation_revisions WHERE simulationId=:simulationId ORDER BY calculatedAt DESC LIMIT 1") suspend fun latestRevision(simulationId: String): SimulationRevisionEntity?
    @Query("SELECT * FROM property_tax_ai_cache WHERE simulationId=:simulationId AND promptVersion=:promptVersion ORDER BY createdAt DESC LIMIT 1") suspend fun latestTaxAnalysis(simulationId: String, promptVersion: String): PropertyTaxAiCacheEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertProperty(value: PropertyEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSimulation(value: SaleSimulationEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRevision(value: SimulationRevisionEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertRule(value: TaxRuleVersionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTaxAnalysis(value: PropertyTaxAiCacheEntity)
    @Query("UPDATE property_tax_rule_versions SET status='ARCHIVED' WHERE id != :activeId") suspend fun archiveRulesExcept(activeId: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertScenario(value: PropertyTaxScenarioEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertScenarioTransaction(value: ScenarioTransactionEntity)
    @Query("SELECT COALESCE(MAX(sequence), 0) FROM property_tax_scenario_transactions WHERE scenarioId=:scenarioId") suspend fun lastScenarioSequence(scenarioId: String): Int
    @Query("DELETE FROM property_tax_properties WHERE id=:id") suspend fun deleteProperty(id: String)
    @Query("DELETE FROM property_tax_simulations WHERE id=:id") suspend fun deleteSimulation(id: String)
    @Query("DELETE FROM property_tax_scenario_transactions WHERE scenarioId=:scenarioId") suspend fun deleteScenarioTransactions(scenarioId: String)
    @Query("DELETE FROM property_tax_scenarios WHERE id=:scenarioId") suspend fun deleteScenarioOnly(scenarioId: String)
}

@Database(
    entities = [PropertyEntity::class, TaxRuleVersionEntity::class, SaleSimulationEntity::class, SimulationRevisionEntity::class, PropertyTaxScenarioEntity::class, ScenarioTransactionEntity::class, PropertyTaxAiCacheEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class PropertyTaxDatabase : RoomDatabase() {
    abstract fun dao(): PropertyTaxDao
    companion object {
        @Volatile private var instance: PropertyTaxDatabase? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, PropertyTaxDatabase::class.java, "property_tax.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE property_tax_properties ADD COLUMN spouseOwnershipRatio REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE property_tax_properties ADD COLUMN regulatedAreaAtAcquisition INTEGER")
                db.execSQL("ALTER TABLE property_tax_properties ADD COLUMN expectedCompletionDate TEXT")
                db.execSQL("ALTER TABLE property_tax_properties ADD COLUMN ownerBirthYear INTEGER")
                db.execSQL("ALTER TABLE property_tax_properties ADD COLUMN spouseBirthYear INTEGER")
                db.execSQL("ALTER TABLE property_tax_simulations ADD COLUMN regulatedAreaAtSale INTEGER")
                db.execSQL("ALTER TABLE property_tax_simulations ADD COLUMN surchargeGraceEligible INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE property_tax_simulations ADD COLUMN postCompletionPresaleSpecialEligible INTEGER NOT NULL DEFAULT 0")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE property_tax_properties ADD COLUMN ownerBirthDate TEXT")
                db.execSQL("ALTER TABLE property_tax_properties ADD COLUMN spouseBirthDate TEXT")
                db.execSQL("ALTER TABLE property_tax_properties ADD COLUMN acquisitionContractDate TEXT")
                db.execSQL("ALTER TABLE property_tax_properties ADD COLUMN urbanAreaTaxApplicable INTEGER")
                db.execSQL("ALTER TABLE property_tax_properties ADD COLUMN annualRegionalResourceTax INTEGER")
                db.execSQL("ALTER TABLE property_tax_properties ADD COLUMN acquisitionRuralSpecialTax INTEGER")
                db.execSQL("ALTER TABLE property_tax_properties ADD COLUMN acquisitionHouseCountTreatment TEXT NOT NULL DEFAULT 'AUTO'")
                db.execSQL("ALTER TABLE property_tax_properties ADD COLUMN capitalGainsHouseCountTreatment TEXT NOT NULL DEFAULT 'AUTO'")
                db.execSQL("ALTER TABLE property_tax_properties ADD COLUMN comprehensiveTaxTreatment TEXT NOT NULL DEFAULT 'AUTO'")
                db.execSQL("ALTER TABLE property_tax_properties ADD COLUMN capitalGainsSurchargeTreatment TEXT NOT NULL DEFAULT 'AUTO'")
                db.execSQL("ALTER TABLE property_tax_properties ADD COLUMN acquisitionSurchargeRelief TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE property_tax_properties ADD COLUMN previousHomeDispositionDate TEXT")
                db.execSQL("ALTER TABLE property_tax_properties ADD COLUMN residenceRequirementExempt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE property_tax_properties ADD COLUMN jointComprehensiveTaxSpecialRequested INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE property_tax_properties ADD COLUMN jointSpecialTaxpayer TEXT")
                db.execSQL("ALTER TABLE property_tax_rule_versions ADD COLUMN effectiveUntil TEXT")
                db.execSQL("ALTER TABLE property_tax_simulations ADD COLUMN saleContractDate TEXT")
                db.execSQL("ALTER TABLE property_tax_simulations ADD COLUMN depositReceived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE property_tax_simulations ADD COLUMN landTransactionPermitRequired INTEGER")
                db.execSQL("ALTER TABLE property_tax_simulations ADD COLUMN landTransactionPermitApplicationDate TEXT")
                db.execSQL("ALTER TABLE property_tax_simulations ADD COLUMN landTransactionPermitApproved INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE property_tax_simulations ADD COLUMN extendedSurchargeGraceRegion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE property_tax_simulations ADD COLUMN completedHomeMoveInDate TEXT")
                db.execSQL("ALTER TABLE property_tax_simulations ADD COLUMN completedHomeResidenceEndDate TEXT")
                db.execSQL("ALTER TABLE property_tax_simulations ADD COLUMN ownerBasicDeductionUsed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE property_tax_simulations ADD COLUMN spouseBasicDeductionUsed INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
