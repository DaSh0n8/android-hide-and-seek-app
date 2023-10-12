package com.example.hideandseek

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

class HideAndSeek : Application() {
    private lateinit var realtimeDb: FirebaseDatabase
    private lateinit var storageDb: FirebaseStorage

    override fun onCreate() {
        super.onCreate()

        // firebase initialization
        FirebaseApp.initializeApp(this)
        val realtimeUrl = "https://db-demo-26f0a-default-rtdb.asia-southeast1.firebasedatabase.app/"
        val storageUrl = "gs://db-demo-26f0a.appspot.com"
        realtimeDb = FirebaseDatabase.getInstance(realtimeUrl)
        storageDb = FirebaseStorage.getInstance(storageUrl)
    }

    /**
     * Getter for Firebase
     */
    fun getRealtimeDb(): FirebaseDatabase {
        return realtimeDb
    }

    fun getStorageDb(): FirebaseStorage {
        return storageDb
    }
}