package com.conupods.OutdoorMaps.View;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.transition.Fade;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentActivity;

import com.conupods.OutdoorMaps.OutdoorBuildingOverlays;
import com.conupods.IndoorMaps.IndoorBuildingOverlays;
import com.conupods.OutdoorMaps.BuildingInfoWindow;
import com.conupods.OutdoorMaps.CameraController;
import com.conupods.OutdoorMaps.MapInitializer;
import com.conupods.OutdoorMaps.View.SearchView.SearchActivity;
import com.conupods.R;
import com.conupods.Settings.SettingsActivity;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.tasks.Task;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = "MapsActivity";

    public static final int LOCATION_PERMISSION_REQUEST_CODE = 1234;
    public static final int RESOLVABLE_API_ERROR_REQUEST_CODE = 51;

    private GoogleMap mMap;
    private OutdoorBuildingOverlays mOutdoorBuildingOverlays;
    private CameraController mCameraController;
    private SearchView mSearchBar;
    private BuildingInfoWindow mBuildingInfoWindow;

    private final String COURSE_LOCATION_PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION;
    private final String FINE_LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION;

    //Variables for logic
    private boolean mPermissionsGranted = false;

    //Providers
    //TODO Might need to update to more recent version
    private FusedLocationProviderClient fusedLocationProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        initializeMap();

        Fade fade = new Fade();
        View decor = getWindow().getDecorView();
        fade.excludeTarget(android.R.id.statusBarBackground, true);
        fade.excludeTarget(android.R.id.navigationBarBackground, true);
        getWindow().setEnterTransition(fade);
        getWindow().setExitTransition(fade);

        if (!mPermissionsGranted)
            getLocationPermission();
        else {
            mCameraController.goToDeviceCurrentLocation();
        }
    }

    private void initializeMap() {
        Log.d(TAG, "Initializing Map...");

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.d(TAG, "Map is ready");

        mMap = googleMap;
        mOutdoorBuildingOverlays = new OutdoorBuildingOverlays(mMap, getString(R.string.geojson_url));
        fusedLocationProvider = LocationServices.getFusedLocationProviderClient(this);
        mCameraController = new CameraController(mMap, mPermissionsGranted, fusedLocationProvider);
        mBuildingInfoWindow = new BuildingInfoWindow(getLayoutInflater());

        if (mPermissionsGranted) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
            createLocationRequest();
        }

        IndoorBuildingOverlays indoorBuildingOverlays = new IndoorBuildingOverlays((View) findViewById(R.id.floorButtonsGroup), mMap);
        MapInitializer mapInitializer = new MapInitializer(mCameraController, indoorBuildingOverlays, mOutdoorBuildingOverlays, mMap, mBuildingInfoWindow);
        mapInitializer.onCameraChange();
        mapInitializer.initializeFloorButtons((View)findViewById(R.id.floorButtonsGroup));
        mSearchBar = (SearchView) mapInitializer.initializeSearchBar((SearchView) findViewById(R.id.searchBar));
        setSearchViewOnClickListener(mSearchBar, (View v) -> { triggerActivityTransition();
        });
        mapInitializer.initializeToggleButtons((Button) findViewById(R.id.SGW), (Button) findViewById(R.id.LOY));
        mapInitializer.initializeLocationButton((Button) findViewById(R.id.locationButton));
        mapInitializer.initializeBuildingMarkers();
        findViewById(R.id.settingsButton).setOnClickListener(view -> {
            startActivityIfNeeded(new Intent(MapsActivity.this, SettingsActivity.class).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT), 0);
        });

        Toast.makeText(this, "Maps is ready", Toast.LENGTH_SHORT).show();
        mOutdoorBuildingOverlays.overlayPolygons();
    }

    private void createLocationRequest() {
        //Building the location request
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder requestBuilder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);

        //Make the requests to the current settings of the device
        SettingsClient settingsClient = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> tasks = settingsClient.checkLocationSettings(requestBuilder.build());

        tasks.addOnSuccessListener(MapsActivity.this, locationSettingsResponse -> mCameraController.moveToLocationAndAddMarker(CameraController.SGW_CAMPUS_LOC));

        tasks.addOnFailureListener(MapsActivity.this, e -> {
            if (e instanceof ResolvableApiException) {
                ResolvableApiException resolvableApiException = (ResolvableApiException) e;

                try {
                    resolvableApiException.startResolutionForResult(MapsActivity.this, RESOLVABLE_API_ERROR_REQUEST_CODE);
                } catch (Exception e1) {
                    Log.e(TAG, "Error in getting settings for location request... ");
                    e1.printStackTrace();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 51 && resultCode == RESULT_OK) {
            mCameraController.goToDeviceCurrentLocation();
        }
    }

    private void getLocationPermission() {
        Log.d(TAG, "Getting Location Permissions");

        /** After android Marshmellow release, we need to explicitly check for
         * permissions such as location permissions*/
        String[] permissions = {
                FINE_LOCATION_PERMISSION, COURSE_LOCATION_PERMISSION};

        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), FINE_LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "FINE_LOCATION_PERMISSION given");

            if (ContextCompat.checkSelfPermission(this.getApplicationContext(), COURSE_LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "COURSE_LOCATION_PERMISSION given");
                mPermissionsGranted = true;
            }
        } else
            ActivityCompat.requestPermissions(MapsActivity.this, permissions, LOCATION_PERMISSION_REQUEST_CODE);
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult is called");

        mPermissionsGranted = false;

        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST_CODE: {
                if (grantResults.length > 0) {
                    for (int i = 0; i < grantResults.length; i++) {
                        if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                            mPermissionsGranted = false;
                            Log.d(TAG, "Permissions Failed " + i);
                            Log.d(TAG, "Permissions Failed");
                            return;
                        }
                    }
                    Log.d(TAG, "Permissions Granted");
                    mPermissionsGranted = true;
                    initializeMap();
                }
            }
        }
    }

    public void triggerActivityTransition() {
        Intent intent = new Intent(MapsActivity.this, SearchActivity.class);
        ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(MapsActivity.this, this.mSearchBar, ViewCompat.getTransitionName(this.mSearchBar));

        startActivity(intent, options.toBundle());
        finish();
    }

    public static void setSearchViewOnClickListener(View v, View.OnClickListener listener) {
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            int count = group.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = group.getChildAt(i);
                if (child instanceof LinearLayout || child instanceof RelativeLayout) {
                    setSearchViewOnClickListener(child, listener);
                }

                if (child instanceof TextView) {
                    TextView text = (TextView) child;
                    text.setFocusable(false);
                }
                child.setOnClickListener(listener);
            }
        }
    }


}
