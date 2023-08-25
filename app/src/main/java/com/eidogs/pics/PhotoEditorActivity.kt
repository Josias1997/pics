package com.eidogs.pics

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnticipateOvershootInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eidogs.pics.base.BaseActivity
import com.eidogs.pics.objects.Constants
import com.eidogs.pics.filters.FilterListener
import com.eidogs.pics.filters.FilterViewAdapter
import com.eidogs.pics.tools.EditingToolsAdapter
import com.eidogs.pics.tools.OnItemSelected
import com.eidogs.pics.tools.ToolType
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import ja.burhanrashid52.photoeditor.OnPhotoEditorListener
import ja.burhanrashid52.photoeditor.PhotoEditor
import ja.burhanrashid52.photoeditor.PhotoEditorView
import ja.burhanrashid52.photoeditor.PhotoFilter
import ja.burhanrashid52.photoeditor.SaveFileResult
import ja.burhanrashid52.photoeditor.SaveSettings
import ja.burhanrashid52.photoeditor.TextStyleBuilder
import ja.burhanrashid52.photoeditor.ViewType
import ja.burhanrashid52.photoeditor.shape.ShapeBuilder
import ja.burhanrashid52.photoeditor.shape.ShapeType
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class PhotoEditorActivity : BaseActivity(), OnPhotoEditorListener,
    View.OnClickListener, PropertiesBSFragment.Properties,
    ShapeBSFrament.Properties, EmojiBSFragment.EmojiListener,
    StickerBSFragment.StickerListener, OnItemSelected, FilterListener {

    lateinit var mPhotoEditor: PhotoEditor;
    private lateinit var mPhotoEditorView: PhotoEditorView
    private lateinit var mPropertiesBSFragment: PropertiesBSFragment
    private lateinit var mShapeBSFrament: ShapeBSFrament
    private lateinit var mShapeBuilder: ShapeBuilder
    private lateinit var mEmojiBSFragment: EmojiBSFragment
    private lateinit var mStickerBSFragment: StickerBSFragment
    private lateinit var mTxtCurrentTool: TextView
    private lateinit var mWonderFont: Typeface
    private lateinit var mRvTools: RecyclerView
    private lateinit var mRvFilters: RecyclerView
    private val mEditingToolsAdapter = EditingToolsAdapter(this)
    private val mFilterAdapter = FilterViewAdapter(this)
    private lateinit var mRootView: ConstraintLayout
    private val mConstraintSet = ConstraintSet()
    private var mIsFilterVisible = false
    private lateinit var mTakePictureLauncher: ActivityResultLauncher<Uri>
    private lateinit var mRequestCameraPermission: ActivityResultLauncher<Array<String>>
    private lateinit var mPickMedia: ActivityResultLauncher<PickVisualMediaRequest>
    private lateinit var mProgressBar: ProgressBar

    @VisibleForTesting
    var mSaveImageUri: Uri? = null

    private lateinit var mTempUri: Uri

    private lateinit var mSaveHelper: FileSaveHelper


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_editor)

        initViews()
        val imageUri = intent.getStringExtra("IMAGE_URI")
        setPhotoEditorImageSource(imageUri)

        mWonderFont = Typeface.createFromAsset(assets, "beyond_wonderland.ttf")

        initFragments()

        val llmTools = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        mRvTools.layoutManager = llmTools
        mRvTools.adapter = mEditingToolsAdapter

        val llmFilters = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        mRvFilters.layoutManager = llmFilters
        mRvFilters.adapter = mFilterAdapter

        mPhotoEditor = PhotoEditor.Builder(this, mPhotoEditorView)
            .setPinchTextScalable(true)
            .build()

        mPhotoEditor.setOnPhotoEditorListener(this)

        mTakePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                Toast.makeText(this, "Temp Uri $mTempUri", Toast.LENGTH_SHORT).show()
                setPhotoEditorImageSource(mTempUri.toString())
            } else {
                Toast.makeText(this, "No picture taken", Toast.LENGTH_SHORT).show()
            }
        }

        mRequestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ resultsMap ->
            resultsMap.forEach {
                if (it.value) {
                    Log.d(PhotoApp.TAG, "Permissions ${it.key} granted")
                }
            }
        }
        mPickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                setPhotoEditorImageSource(uri.toString())
            } else {
                Toast.makeText(this, "No media selected", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        checkCameraPermission()

        mSaveHelper = FileSaveHelper(this)
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {

            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
               val builder = AlertDialog.Builder(this)
                builder.setMessage("Grant permissions to access camera")
                builder.setPositiveButton("Yes") {
                        dialog, _ -> run {
                            dialog.cancel()
                            mRequestCameraPermission.launch(arrayOf(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.CAMERA
                            ))
                } }
                builder.setNegativeButton("No") {
                    dialog, _ -> run {
                    dialog.cancel()
                    Toast.makeText(this, "You denied us access to camera app", Toast.LENGTH_SHORT).show()
                } }
                builder.create().show()
            } else -> {
                mRequestCameraPermission.launch(arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA
                ))
            }
        }

    }
    private fun launchGallery() {
        mPickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun initFragments() {
        mPropertiesBSFragment = PropertiesBSFragment()
        mEmojiBSFragment = EmojiBSFragment()
        mStickerBSFragment = StickerBSFragment()
        mShapeBSFrament = ShapeBSFrament()

        mStickerBSFragment.setStickerListener(this)
        mEmojiBSFragment.setEmojiListener(this)
        mPropertiesBSFragment.setPropertiesChangeListener(this)
        mShapeBSFrament.setPropertiesChangeListener(this)
    }

    private fun setPhotoEditorImageSource(imageUri: String?) {
        mPhotoEditorView.source.setImageURI(Uri.parse(imageUri))
    }

    private fun initViews() {
        mPhotoEditorView = findViewById(R.id.photoEditorView)
        mTxtCurrentTool = findViewById(R.id.txtCurrentTool)
        mRvTools = findViewById(R.id.rvConstraintTools)
        mRvFilters = findViewById(R.id.rvFilterView)
        mRootView = findViewById(R.id.rootView)
        mProgressBar = findViewById(R.id.progress_bar)

        val imgUndo: ImageView = findViewById(R.id.imgUndo)
        imgUndo.setOnClickListener(this)

        val imgRedo: ImageView = findViewById(R.id.imgRedo)
        imgRedo.setOnClickListener(this)

        val imgCamera: ImageView = findViewById(R.id.imgCamera)
        imgCamera.setOnClickListener(this)

        val imgGallery: ImageView = findViewById(R.id.imgGallery)
        imgGallery.setOnClickListener(this)

        val imgSave: ImageView = findViewById(R.id.imgSave)
        imgSave.setOnClickListener(this)

        val imgClose: ImageView = findViewById(R.id.imgClose)
        imgClose.setOnClickListener(this)

        val imgShare: ImageView = findViewById(R.id.imgShare)
        imgShare.setOnClickListener(this)
    }

    override fun onAddViewListener(viewType: ViewType?, numberOfAddedViews: Int) {
        Log.d(
            PhotoApp.TAG,
            "onAddViewListener() called with: viewType = [$viewType], numberOfAddedViews = [$numberOfAddedViews]"
        )
    }

    override fun onEditTextChangeListener(rootView: View?, text: String?, colorCode: Int) {
        val textEditDialogFragment = TextEditDialogFragment.show(this, text.toString(), colorCode)
        textEditDialogFragment.setOnTextEditorListener(
            object : TextEditDialogFragment.TextEditorListener {
                override fun onDone(inputText: String, colorCode: Int) {
                    val styleBuilder = TextStyleBuilder()
                    styleBuilder.withTextColor(colorCode)
                    mPhotoEditor.editText(rootView!!, inputText, styleBuilder)
                    mTxtCurrentTool.setText(R.string.label_text)
                }
            }
        )
    }

    override fun onRemoveViewListener(viewType: ViewType?, numberOfAddedViews: Int) {
        Log.d(
            PhotoApp.TAG,
            "onRemoveViewListener() called with: viewType = [$viewType], numberOfAddedViews = [$numberOfAddedViews]"
        )
    }

    override fun onStartViewChangeListener(viewType: ViewType?) {
        Log.d(PhotoApp.TAG, "onStartViewChangeListener() called with: viewType = [$viewType]")
    }

    override fun onStopViewChangeListener(viewType: ViewType?) {
        Log.d(PhotoApp.TAG, "onStopViewChangeListener() called with: viewType = [$viewType]")
    }

    override fun onTouchSourceImage(event: MotionEvent?) {
        Log.d(PhotoApp.TAG, "onTouchView() called with: event = [$event]")
    }

    @SuppressLint("NonConstantResourceId", "MissingPermission")
    override fun onClick(view: View?) {
       when(view!!.id) {
           R.id.imgUndo -> mPhotoEditor.undo()
           R.id.imgRedo -> mPhotoEditor.redo()
           R.id.imgSave -> saveImage()
           R.id.imgShare -> shareImage()
           R.id.imgCamera -> {
               val file: File = createImageFile()
               mTempUri = FileProvider.getUriForFile(this, Constants.FILE_PROVIDER_AUTHORITY, file)
               mTakePictureLauncher.launch(mTempUri)
           }
           R.id.imgGallery -> launchGallery()
           R.id.imgClose -> {
               startActivity(Intent(this, MainActivity::class.java))
               finish()
           }
       }
    }


    @RequiresPermission(allOf = [Manifest.permission.WRITE_EXTERNAL_STORAGE])
    private fun saveImage() {
        val fileName = System.currentTimeMillis().toString() + ".png"
        val hasStoragePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        if(hasStoragePermission || FileSaveHelper.isSdkHigherThan28()) {
            mProgressBar.visibility = View.VISIBLE
            mSaveHelper.createFile(fileName, object : FileSaveHelper.OnFileCreateResult {
                @RequiresPermission(allOf = [Manifest.permission.WRITE_EXTERNAL_STORAGE])
                override fun onFileCreatedResult(
                    created: Boolean,
                    filePath: String?,
                    error: String?,
                    uri: Uri?
                ) {
                    lifecycleScope.launch {
                        if(created && filePath != null) {
                            val saveSettings = SaveSettings.Builder()
                                .setClearViewsEnabled(true)
                                .setTransparencyEnabled(true)
                                .build()
                            val result = mPhotoEditor.saveAsFile(filePath, saveSettings)
                            if(result is SaveFileResult.Success) {
                                mSaveHelper.notifyThatFileIsNowPubliclyAvailable(contentResolver)
                                mProgressBar.visibility = View.GONE
                                mSaveImageUri = uri
                                Toast.makeText(applicationContext, "Image saved successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                mProgressBar.visibility = View.GONE
                                error?.let {
                                    showSnackbar(error)
                                }
                            }
                        }

                    }
                }
            })
        } else {
            requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun shareImage() {
        val saveImageUri = mSaveImageUri
        if(saveImageUri == null) {
            showSnackbar(getString(R.string.msg_save_image_to_share))
            return
        }
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_STREAM, buildFileProviderUri(saveImageUri))
        startActivity(Intent.createChooser(intent, getString(R.string.msg_share_image)))
    }

    private fun buildFileProviderUri(uri: Uri): Uri {
        if(FileSaveHelper.isSdkHigherThan28()) {
            return uri
        }
        val path: String = uri.path ?: throw IllegalArgumentException("URI Path Expected")
        return FileProvider.getUriForFile(
            this,
            Constants.FILE_PROVIDER_AUTHORITY,
            File(path)
        )
    }

    override fun onEmojiClick(emojiUnicode: String) {
        mPhotoEditor.addEmoji(emojiUnicode)
        mTxtCurrentTool.setText(R.string.label_emoji)
    }

    override fun onColorChanged(colorCode: Int) {
        mPhotoEditor.setShape(mShapeBuilder.withShapeColor(colorCode))
        mTxtCurrentTool.setText(R.string.label_text)
    }

    override fun onOpacityChanged(opacity: Int) {
        mPhotoEditor.setShape(mShapeBuilder.withShapeOpacity(opacity))
        mTxtCurrentTool.setText(R.string.label_text)
    }

    override fun onShapePicked(shapeType: ShapeType) {
        mPhotoEditor.setShape(mShapeBuilder.withShapeType(shapeType))
    }

    override fun onShapeSizeChanged(shapeSize: Int) {
        mPhotoEditor.setShape(mShapeBuilder.withShapeSize(shapeSize.toFloat()))
        mTxtCurrentTool.setText(R.string.label_text)
    }

    override fun onStickerClick(bitmap: Bitmap) {
        mPhotoEditor.addImage(bitmap)
        mTxtCurrentTool.setText(R.string.label_sticker)
    }

    override fun onFilterSelected(photoFilter: PhotoFilter) {
        mPhotoEditor.setFilterEffect(photoFilter)
    }

    override fun onToolSelected(toolType: ToolType) {
        when(toolType) {
            ToolType.SHAPE -> {
                mPhotoEditor.setBrushDrawingMode(true)
                mShapeBuilder = ShapeBuilder()
                mPhotoEditor.setShape(mShapeBuilder)
                mTxtCurrentTool.setText(R.string.label_shape)
                showBottomSheetDialogFragment(mShapeBSFrament)
            }
            ToolType.TEXT -> {
                val textEditDialogFragment = TextEditDialogFragment.show(this)
                textEditDialogFragment.setOnTextEditorListener(object: TextEditDialogFragment.TextEditorListener {
                    override fun onDone(inputText: String, colorCode: Int) {
                        val styleBuilder = TextStyleBuilder()
                        styleBuilder.withTextColor(colorCode)
                        mPhotoEditor.addText(inputText, styleBuilder)
                        mTxtCurrentTool.setText(R.string.label_text)
                    }
                })
            }
            ToolType.ERASER -> {
                mPhotoEditor.brushEraser()
                mTxtCurrentTool.setText(R.string.label_eraser_mode)
            }
            ToolType.FILTER -> {
                mTxtCurrentTool.setText(R.string.label_filter)
                showFilter(true)
            }
            ToolType.EMOJI -> showBottomSheetDialogFragment(mEmojiBSFragment)
            ToolType.STICKER -> showBottomSheetDialogFragment(mStickerBSFragment)
        }
    }

    private fun showBottomSheetDialogFragment(fragment: BottomSheetDialogFragment?) {
        if(fragment == null || fragment.isAdded) {
            return
        }
        fragment.show(supportFragmentManager, fragment.tag)
    }

    private fun showFilter(isVisible: Boolean) {
        mIsFilterVisible = isVisible
        mConstraintSet.clone(mRootView)
        val rvFilterId: Int = mRvFilters.id

        if(isVisible) {
            mConstraintSet.clear(rvFilterId, ConstraintSet.START)
            mConstraintSet.connect(
                rvFilterId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START
            )
            mConstraintSet.connect(
                rvFilterId,ConstraintSet.END,
                ConstraintSet.PARENT_ID, ConstraintSet.END
            )

        } else {
            mConstraintSet.connect(
                rvFilterId, ConstraintSet.START,
                ConstraintSet.PARENT_ID, ConstraintSet.END
            )
            mConstraintSet.clear(rvFilterId, ConstraintSet.END)
        }

        val changeBounds = ChangeBounds()
        changeBounds.duration = 350
        changeBounds.interpolator = AnticipateOvershootInterpolator(1.0f)
        TransitionManager.beginDelayedTransition(mRootView, changeBounds)

        mConstraintSet.applyTo(mRootView)
    }

    override fun onBackPressed() {
        if(mIsFilterVisible) {
            showFilter(false)
        } else if (!mPhotoEditor.isCacheEmpty) {
           //
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        private const val TAG = "PhotoEditorActivity"
    }
}