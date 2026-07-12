package com.fengnian.folderdrawer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.fengnian.folderdrawer.data.AppDatabase
import com.fengnian.folderdrawer.data.AppItem
import com.fengnian.folderdrawer.data.Collection
import com.fengnian.folderdrawer.data.CollectionRepository
import com.fengnian.folderdrawer.iconpack.IconPackManager

class CollectionViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    val repository: CollectionRepository = CollectionRepository(
        context = app,
        collectionDao = db.collectionDao(),
        appItemDao = db.appItemDao(),
        iconPackManager = IconPackManager.getInstance(app)
    )

    val collections: LiveData<List<Collection>> = repository.allCollections

    fun observeCollection(id: Long) = repository.observeCollection(id)
    fun observeApps(collectionId: Long) = repository.observeApps(collectionId)
}
