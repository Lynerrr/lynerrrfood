package com.lynerrr.food

import android.app.Application
import com.lynerrr.food.data.AiSettings
import com.lynerrr.food.data.AppUpdater
import com.lynerrr.food.data.AppDatabase
import com.lynerrr.food.data.BundledDatabase
import com.lynerrr.food.data.FoodRepository
import com.lynerrr.food.data.ProductsDatabase
import com.lynerrr.food.data.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FoodApp : Application() {
    lateinit var repository: FoodRepository
        private set
    lateinit var sync: SyncManager
        private set
    lateinit var products: ProductsDatabase
        private set
    lateinit var ai: AiSettings
        private set
    lateinit var updater: AppUpdater
        private set

    override fun onCreate() {
        super.onCreate()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val db = AppDatabase.create(this)
        repository = FoodRepository(db, scope)
        products = ProductsDatabase(this, db)
        ai = AiSettings(this)
        sync = SyncManager(BundledDatabase(this, db), products, scope)
        sync.importBundledIfNeeded()
        updater = AppUpdater(this, BuildConfig.UPDATES_REPO, scope)
    }
}
