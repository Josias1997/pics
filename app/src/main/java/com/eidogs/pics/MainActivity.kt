package com.eidogs.pics

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.eidogs.pics.base.BaseActivity
import com.eidogs.pics.databinding.ActivityMainBinding
import com.eidogs.pics.objects.Constants
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity :  BaseActivity() {

    private lateinit var mTempUri: Uri
    private lateinit var mAdView: AdView
    private lateinit var mAdView2: AdView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        MobileAds.initialize(this) {}
        mAdView = findViewById(R.id.banner_ad)
        mAdView2 = findViewById(R.id.banner_ad2)
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)
        mAdView2.loadAd(adRequest)

        val pickMedia =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) {
                    goToEditActivity(uri.toString())
                } else {
                    Toast.makeText(this@MainActivity, "No media selected", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                goToEditActivity(mTempUri.toString())
            } else {
                Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        }
        binding.wand.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ImageOnly))
        }

        binding.camera.setOnClickListener {
            val file = createImageFile()
            mTempUri = FileProvider.getUriForFile(this, Constants.FILE_PROVIDER_AUTHORITY, file)
            takePicture.launch(mTempUri)
        }
    }



    private fun goToEditActivity(path: String) {
        val intent = Intent(this@MainActivity, PhotoEditorActivity::class.java)
        intent.putExtra("IMAGE_URI", path)
        startActivity(intent)
    }
}