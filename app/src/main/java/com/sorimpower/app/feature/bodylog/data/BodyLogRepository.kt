package com.sorimpower.app.feature.bodylog.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import com.sorimpower.app.feature.bodylog.domain.localDate

private val Context.bodyLogDataStore by preferencesDataStore("body_log_settings")

data class BodyLogData(
    val weights: List<WeightEntryEntity>,
    val meals: List<MealWithDetails>,
    val goal: WeightGoalEntity?,
    val mounjaroInjections: List<MounjaroInjectionEntity>,
    val weightsHidden: Boolean,
    val quickMealTemplates: List<MealQuickTemplate>,
    val dailyCalories: List<DailyCalorieSummaryEntity>,
    val mealCalories: List<MealCalorieEstimateEntity>,
    val exercises: List<ExerciseEntryEntity>,
    val inBodyResults: List<InBodyResultEntity>,
)

data class ImportedInBodyFile(val localPath: String, val displayName: String, val mimeType: String)

data class SavedMealResult(val mealId: String, val calorieAnalysisMealIds: List<String>)

data class MealItemInput(val name: String, val amount: String = "")
data class MealQuickTemplate(
    val id: String,
    val mealType: String,
    val items: List<String>,
    val note: String?,
    val tags: Set<String>,
)

class BodyLogRepository(private val context: Context) {
    private val dao = BodyLogDatabase.get(context).dao()
    private val weightsHiddenKey = booleanPreferencesKey("weights_hidden")
    private val weightsHidden = context.bodyLogDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[weightsHiddenKey] ?: false }
    private val quickMealTemplates = context.bodyLogDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> parseQuickMealTemplates(preferences[quickMealTemplatesKey]) }
    val data = combine(
        dao.observeWeights(),
        dao.observeMeals(),
        dao.observeActiveGoal(),
        dao.observeMounjaroInjections(),
        weightsHidden,
    ) { weights, meals, goal, injections, hidden ->
        BodyLogData(weights, meals, goal, injections, hidden, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }.combine(quickMealTemplates) { data, templates -> data.copy(quickMealTemplates = templates) }
        .combine(dao.observeDailyCalorieSummaries()) { data, calories -> data.copy(dailyCalories = calories) }
        .combine(dao.observeMealCalorieEstimates()) { data, calories -> data.copy(mealCalories = calories) }
        .combine(dao.observeExercises()) { data, exercises -> data.copy(exercises = exercises) }
        .combine(dao.observeInBodyResults()) { data, results -> data.copy(inBodyResults = results) }

    init {
        CoroutineScope(Dispatchers.IO).launch { cleanupOrphanedMealPhotos() }
    }

    suspend fun setWeightsHidden(hidden: Boolean) {
        context.bodyLogDataStore.edit { preferences -> preferences[weightsHiddenKey] = hidden }
    }

    suspend fun mealIdsWithoutCalorieEstimate(): List<String> = withContext(Dispatchers.IO) {
        dao.mealIdsWithoutCalorieEstimate()
    }

    suspend fun saveQuickMealTemplate(mealType: String, items: List<String>, note: String?, tags: Set<String>) {
        val cleanItems = items.map(String::trim).filter(String::isNotBlank).take(12)
        if (cleanItems.isEmpty()) return
        context.bodyLogDataStore.edit { preferences ->
            val updated = parseQuickMealTemplates(preferences[quickMealTemplatesKey]) + MealQuickTemplate(
                id = UUID.randomUUID().toString(),
                mealType = mealType,
                items = cleanItems,
                note = note?.trim()?.take(200)?.ifBlank { null },
                tags = tags,
            )
            preferences[quickMealTemplatesKey] = updated.takeLast(MAX_QUICK_MEAL_TEMPLATES).quickMealTemplatesJson()
        }
    }

    suspend fun deleteQuickMealTemplate(id: String) {
        context.bodyLogDataStore.edit { preferences ->
            preferences[quickMealTemplatesKey] = parseQuickMealTemplates(preferences[quickMealTemplatesKey])
                .filterNot { it.id == id }.quickMealTemplatesJson()
        }
    }

    suspend fun saveWeight(
        id: String? = null,
        weightKg: Double,
        measuredAt: Long,
        bodyFatPercent: Double?,
        condition: String?,
        note: String?,
    ) {
        val now = System.currentTimeMillis()
        dao.upsertWeight(WeightEntryEntity(
            id = id ?: UUID.randomUUID().toString(),
            weightKg = weightKg,
            measuredAt = measuredAt,
            zoneOffsetMinutes = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(measuredAt), ZoneId.systemDefault()).offset.totalSeconds / 60,
            bodyFatPercent = bodyFatPercent,
            condition = condition,
            note = note?.trim()?.take(200)?.ifBlank { null },
            createdAt = now,
            updatedAt = now,
        ))
    }

    suspend fun deleteWeight(entry: WeightEntryEntity) = dao.deleteWeight(entry)

    suspend fun saveGoal(startWeightKg: Double, targetWeightKg: Double, targetDate: LocalDate?) {
        dao.cancelActiveGoals()
        dao.upsertGoal(WeightGoalEntity(
            id = UUID.randomUUID().toString(),
            startWeightKg = startWeightKg,
            targetWeightKg = targetWeightKg,
            startedOnEpochDay = LocalDate.now().toEpochDay(),
            targetDateEpochDay = targetDate?.toEpochDay(),
            status = "ACTIVE",
        ))
    }

    suspend fun saveMounjaroInjection(
        existing: MounjaroInjectionEntity? = null,
        injectedAt: Long,
        doseMg: Double,
        sideEffects: Set<String>,
        note: String?,
        reminderEnabled: Boolean,
        reminderIntervalWeeks: Int,
    ) {
        val now = System.currentTimeMillis()
        dao.upsertMounjaroInjection(MounjaroInjectionEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            injectedAt = injectedAt,
            doseMg = doseMg,
            sideEffects = sideEffects.joinToString("|"),
            note = note?.trim()?.take(300)?.ifBlank { null },
            reminderEnabled = reminderEnabled,
            reminderIntervalWeeks = reminderIntervalWeeks.coerceIn(1, 4),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        ))
    }

    suspend fun updateMounjaroReminder(injection: MounjaroInjectionEntity, enabled: Boolean, intervalWeeks: Int) {
        dao.upsertMounjaroInjection(injection.copy(
            reminderEnabled = enabled,
            reminderIntervalWeeks = intervalWeeks.coerceIn(1, 4),
            updatedAt = System.currentTimeMillis(),
        ))
    }

    suspend fun deleteMounjaroInjection(injection: MounjaroInjectionEntity) {
        dao.deleteMounjaroInjection(injection)
    }

    suspend fun latestMounjaroInjection(): MounjaroInjectionEntity? = dao.latestMounjaroInjection()

    suspend fun saveExercise(
        existing: ExerciseEntryEntity? = null,
        exercisedAt: Long,
        exerciseType: String,
        durationMinutes: Int,
        intensity: String,
        caloriesBurned: Int?,
        note: String?,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.upsertExercise(ExerciseEntryEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            exercisedAt = exercisedAt,
            exerciseType = exerciseType.trim().take(60),
            durationMinutes = durationMinutes.coerceIn(1, 1_440),
            intensity = intensity.trim().take(20),
            caloriesBurned = caloriesBurned?.coerceIn(1, 10_000),
            note = note?.trim()?.take(300)?.ifBlank { null },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        ))
    }

    suspend fun deleteExercise(value: ExerciseEntryEntity) = withContext(Dispatchers.IO) { dao.deleteExercise(value) }

    suspend fun importInBodyFile(uri: Uri): ImportedInBodyFile = withContext(Dispatchers.IO) {
        val metadata = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null else {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                name to if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
            }
        }
        val displayName = metadata?.first?.take(120) ?: "인바디_${System.currentTimeMillis()}"
        val mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank {
            if (displayName.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/jpeg"
        }
        require(mimeType == "application/pdf" || mimeType.startsWith("image/")) { "인바디 결과지는 PDF 또는 이미지로 등록해 주세요." }
        metadata?.second?.takeIf { it > MAX_INBODY_FILE_BYTES }?.let { error("인바디 파일은 15MB 이하여야 해요.") }
        val extension = displayName.substringAfterLast('.', if (mimeType == "application/pdf") "pdf" else "jpg")
            .filter(Char::isLetterOrDigit).take(8).ifBlank { "bin" }
        val directory = File(context.filesDir, "inbody_files").apply { mkdirs() }
        val target = File(directory, "${UUID.randomUUID()}.$extension")
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
        } ?: error("선택한 인바디 파일을 읽을 수 없어요.")
        if (copied > MAX_INBODY_FILE_BYTES) {
            target.delete()
            error("인바디 파일은 15MB 이하여야 해요.")
        }
        ImportedInBodyFile(target.absolutePath, displayName, mimeType)
    }

    suspend fun saveInBodyResult(value: InBodyResultEntity) = withContext(Dispatchers.IO) { dao.upsertInBodyResult(value) }

    suspend fun deleteInBodyResult(value: InBodyResultEntity) = withContext(Dispatchers.IO) {
        dao.deleteInBodyResult(value)
        value.originalFilePath.takeIf(String::isNotBlank)?.let { File(it).delete() }
    }

    suspend fun saveMeal(
        existing: MealWithDetails? = null,
        mealType: String,
        eatenAt: Long,
        items: List<MealItemInput>,
        note: String?,
        tags: Set<String>,
        photoUris: List<Uri>,
        retainedPhotoIds: Set<String> = emptySet(),
    ): SavedMealResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val mealId = existing?.meal?.id ?: UUID.randomUUID().toString()
        val previousDate = existing?.meal?.localDate()
        val meal = MealEntryEntity(
            id = mealId,
            mealType = mealType,
            eatenAt = eatenAt,
            zoneOffsetMinutes = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(eatenAt), ZoneId.systemDefault()).offset.totalSeconds / 60,
            note = note?.trim()?.take(200)?.ifBlank { null },
            tags = tags.joinToString("|"),
            createdAt = existing?.meal?.createdAt ?: now,
            updatedAt = now,
        )
        val entities = items.filter { it.name.isNotBlank() }.mapIndexed { index, item ->
            MealItemEntity(UUID.randomUUID().toString(), mealId, item.name.trim().take(60), item.amount.trim().take(30).ifBlank { null }, index)
        }
        val retainedPhotos = existing?.photos.orEmpty().filter { it.id in retainedPhotoIds }
        val removedPhotos = existing?.photos.orEmpty().filterNot { it.id in retainedPhotoIds }
        val available = (3 - retainedPhotos.size).coerceAtLeast(0)
        val imported = photoUris.take(available).mapIndexedNotNull { index, uri -> importPhoto(uri, mealId, retainedPhotos.size + index) }
        val photos = retainedPhotos.mapIndexed { index, photo -> photo.copy(sortOrder = index) } + imported
        try {
            dao.replaceMeal(meal, entities, photos)
            removedPhotos.forEach(::deletePhotoFiles)
        } finally {
            cleanupOrphanedMealPhotos()
        }
        val affectedDates = setOfNotNull(previousDate, meal.localDate())
        affectedDates.forEach { rebuildDailyCalorieSummary(it) }
        val missing = affectedDates.flatMap { date ->
            val meals = mealsForDate(date)
            val estimatedIds = if (meals.isEmpty()) emptySet() else dao.mealCalorieEstimates(meals.map { it.meal.id }).mapTo(mutableSetOf(), MealCalorieEstimateEntity::mealId)
            meals.map { it.meal.id }.filterNot { it in estimatedIds }
        }.distinct()
        SavedMealResult(mealId, missing)
    }

    suspend fun deleteMeal(meal: MealWithDetails) = withContext(Dispatchers.IO) {
        val date = meal.meal.localDate()
        dao.deleteMealById(meal.meal.id)
        meal.photos.forEach { photo ->
            deletePhotoFiles(photo)
        }
        cleanupOrphanedMealPhotos()
        rebuildDailyCalorieSummary(date)
    }

    suspend fun analyzeMealCalories(mealId: String): MealCalorieEstimateEntity? = withContext(Dispatchers.IO) {
        val meal = dao.meal(mealId) ?: return@withContext null
        val sourceHash = meal.calorieSourceHash()
        dao.mealCalorieEstimate(mealId)?.takeIf { it.sourceHash == sourceHash }?.let { return@withContext it }
        val result = OpenAiMealCalorieAnalyzer(context).analyze(meal)
        val latest = dao.meal(mealId) ?: return@withContext null
        if (latest.calorieSourceHash() != sourceHash) return@withContext null
        val estimate = MealCalorieEstimateEntity(
            mealId = mealId,
            estimatedCalories = result.estimatedCalories,
            summary = result.summary,
            sourceHash = sourceHash,
            analyzedAt = System.currentTimeMillis(),
        )
        dao.upsertMealCalorieEstimate(estimate)
        rebuildDailyCalorieSummary(latest.meal.localDate())
        estimate
    }

    private suspend fun rebuildDailyCalorieSummary(date: LocalDate): DailyCalorieSummaryEntity? {
        val meals = mealsForDate(date)
        if (meals.isEmpty()) {
            dao.deleteDailyCalorieSummary(date.toEpochDay())
            return null
        }
        val estimates = dao.mealCalorieEstimates(meals.map { it.meal.id })
        if (estimates.isEmpty()) {
            dao.deleteDailyCalorieSummary(date.toEpochDay())
            return null
        }
        val coverage = if (estimates.size == meals.size) "${meals.size}건" else "${estimates.size}/${meals.size}건"
        val summary = DailyCalorieSummaryEntity(
            dateEpochDay = date.toEpochDay(),
            estimatedCalories = estimates.sumOf(MealCalorieEstimateEntity::estimatedCalories),
            summary = "개별 식사 $coverage 합산",
            mealCount = meals.size,
            analyzedAt = System.currentTimeMillis(),
        )
        dao.upsertDailyCalorieSummary(summary)
        return summary
    }

    private suspend fun mealsForDate(date: LocalDate): List<MealWithDetails> {
        val zone = ZoneId.systemDefault()
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val until = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.mealsBetween(from, until)
    }

    fun createCameraUri(): Pair<Uri, File> {
        val directory = File(context.cacheDir, "meal_camera").apply { mkdirs() }
        val file = File.createTempFile("meal_", ".jpg", directory)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) to file
    }

    private fun importPhoto(uri: Uri, mealId: String, index: Int): MealPhotoEntity? = runCatching {
        val bitmap = decodeMealPhoto(uri) ?: return null
        val scaled = bitmap.scaledTo(MAX_PHOTO_SIDE)
        val thumb = scaled.scaledTo(THUMBNAIL_SIDE)
        val directory = File(context.filesDir, "meal_photos").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val imageFile = File(directory, "$id.jpg")
        val thumbFile = File(directory, "${id}_thumb.jpg")
        imageFile.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        thumbFile.outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 78, it) }
        val outputWidth = scaled.width
        val outputHeight = scaled.height
        if (scaled !== bitmap) scaled.recycle()
        if (thumb !== scaled) thumb.recycle()
        bitmap.recycle()
        MealPhotoEntity(id, mealId, imageFile.absolutePath, thumbFile.absolutePath, outputWidth, outputHeight, index, System.currentTimeMillis())
    }.getOrNull()

    private fun decodeMealPhoto(uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val (targetWidth, targetHeight) = targetPhotoSize(info.size.width, info.size.height)
                decoder.setTargetSize(targetWidth, targetHeight)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = photoSampleSize(bounds.outWidth, bounds.outHeight)
            }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    }

    private fun targetPhotoSize(width: Int, height: Int): Pair<Int, Int> {
        val halfWidth = (width / 2).coerceAtLeast(1)
        val halfHeight = (height / 2).coerceAtLeast(1)
        val largest = maxOf(halfWidth, halfHeight)
        if (largest <= MAX_PHOTO_SIDE) return halfWidth to halfHeight
        val ratio = MAX_PHOTO_SIDE.toFloat() / largest
        return (halfWidth * ratio).toInt().coerceAtLeast(1) to (halfHeight * ratio).toInt().coerceAtLeast(1)
    }

    private fun photoSampleSize(width: Int, height: Int): Int {
        var sampleSize = 2
        while (maxOf(width / sampleSize, height / sampleSize) > MAX_PHOTO_SIDE) sampleSize *= 2
        return sampleSize
    }

    private fun deletePhotoFiles(photo: MealPhotoEntity) {
        File(photo.localPath).delete()
        File(photo.thumbnailPath).delete()
    }

    private suspend fun cleanupOrphanedMealPhotos() {
        val referencedPaths = dao.allMealPhotoPaths()
            .flatMap { listOf(it.localPath, it.thumbnailPath) }
            .toSet()
        File(context.filesDir, "meal_photos").listFiles()
            ?.filter { it.isFile && it.extension.equals("jpg", ignoreCase = true) && it.absolutePath !in referencedPaths }
            ?.forEach(File::delete)
    }

    private fun Bitmap.scaledTo(maxSide: Int): Bitmap {
        val largest = maxOf(width, height)
        if (largest <= maxSide) return this
        val ratio = maxSide.toFloat() / largest
        return Bitmap.createScaledBitmap(this, (width * ratio).toInt(), (height * ratio).toInt(), true)
    }

    private companion object {
        const val MAX_PHOTO_SIDE = 2048
        const val THUMBNAIL_SIDE = 360
        val quickMealTemplatesKey = stringPreferencesKey("quick_meal_templates")
        const val MAX_QUICK_MEAL_TEMPLATES = 12
        const val MAX_INBODY_FILE_BYTES = 15L * 1024L * 1024L
    }
}

private fun parseQuickMealTemplates(source: String?): List<MealQuickTemplate> = runCatching {
    val array = JSONArray(source.orEmpty())
    buildList {
        for (index in 0 until array.length()) {
            val value = array.optJSONObject(index) ?: continue
            val items = value.optJSONArray("items")?.let { foods ->
                buildList { for (foodIndex in 0 until foods.length()) foods.optString(foodIndex).trim().takeIf(String::isNotBlank)?.let(::add) }
            }.orEmpty()
            if (items.isNotEmpty()) add(MealQuickTemplate(
                id = value.optString("id").ifBlank { UUID.randomUUID().toString() },
                mealType = value.optString("mealType"),
                items = items,
                note = value.optString("note").ifBlank { null },
                tags = value.optJSONArray("tags")?.let { tags -> buildSet { for (tagIndex in 0 until tags.length()) tags.optString(tagIndex).trim().takeIf(String::isNotBlank)?.let(::add) } }.orEmpty(),
            ))
        }
    }
}.getOrDefault(emptyList())

private fun List<MealQuickTemplate>.quickMealTemplatesJson(): String = JSONArray().apply {
    forEach { template -> put(JSONObject().apply {
        put("id", template.id); put("mealType", template.mealType); put("items", JSONArray(template.items)); put("note", template.note); put("tags", JSONArray(template.tags.toList()))
    }) }
}.toString()

private fun MealWithDetails.calorieSourceHash(): String {
    val source = buildString {
        append(meal.mealType).append('|').append(meal.eatenAt).append('|').append(meal.note.orEmpty()).append('|').append(meal.tags)
        items.sortedBy(MealItemEntity::sortOrder).forEach { append('|').append(it.name).append(':').append(it.amount.orEmpty()) }
    }
    return MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString("") { "%02x".format(it) }
}
