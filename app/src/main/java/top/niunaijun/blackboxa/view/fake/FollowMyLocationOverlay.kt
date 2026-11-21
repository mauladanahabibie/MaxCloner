package top.niunaijun.blackboxa.view.fake

import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import java.util.Locale
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import top.niunaijun.blackbox.entity.location.BLocation
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.databinding.ActivityOsmdroidBinding
import top.niunaijun.blackboxa.util.inflate
import top.niunaijun.blackboxa.view.base.BaseActivity

class FollowMyLocationOverlay : BaseActivity() {

    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1
    private val binding: ActivityOsmdroidBinding by inflate()
    lateinit var startPoint: GeoPoint
    private lateinit var startMarker: Marker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        setContentView(binding.root)
        setupEdgeToEdge(
            topView = binding.headerContainer,
            bottomView = binding.bottomActionBar,
            applyBottomAsMargin = true
        )

        initToolbar(binding.toolbarLayout.toolbar, R.string.fake_location, true) {
            setResultOnly(startPoint)
        }

        val location: BLocation? = intent.getParcelableExtra("location")
        startPoint = if (location == null) {
            GeoPoint(-6.2088, 106.8456)
        } else {
            GeoPoint(location.latitude, location.longitude)
        }

        updateCoordinateLabel(startPoint)

        startMarker = Marker(binding.map).apply {
            position = startPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        binding.map.overlays.add(startMarker)

        val eventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                startPoint = p
                startMarker.position = p
                binding.map.invalidate()
                updateCoordinateLabel(p)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        }
        binding.map.overlays.add(MapEventsOverlay(eventsReceiver))

        binding.map.controller.setZoom(13.0)
        binding.map.controller.setCenter(startPoint)
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.setMultiTouchControls(true)

        binding.btnSaveLocation.setOnClickListener {
            finishWithResult(startPoint)
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishWithResult(startPoint)
                }
            }
        )
    }

    private fun updateCoordinateLabel(geoPoint: GeoPoint) {
        binding.tvCoordinates.text = String.format(
            Locale.US,
            "%.5f, %.5f",
            geoPoint.latitude,
            geoPoint.longitude
        )
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permissionsToRequest = ArrayList<String>()
        var i = 0
        while (i < grantResults.size && i < permissions.size) {
            permissionsToRequest.add(permissions[i])
            i++
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private fun setResultOnly(geoPoint: GeoPoint) {
        intent.putExtra("latitude", geoPoint.latitude)
        intent.putExtra("longitude", geoPoint.longitude)
        setResult(Activity.RESULT_OK, intent)
    }

    private fun finishWithResult(geoPoint: GeoPoint) {
        setResultOnly(geoPoint)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        window.peekDecorView()?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
        finish()
    }
}
