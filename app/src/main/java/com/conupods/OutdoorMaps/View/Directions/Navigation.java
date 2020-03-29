package com.conupods.OutdoorMaps.View.Directions;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.conupods.OutdoorMaps.Services.OutdoorDirectionsService;
import com.conupods.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.PendingResult;
import com.google.maps.internal.PolylineEncoding;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.DirectionsStep;
import com.google.maps.model.Duration;
import com.google.maps.model.TravelMode;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class Navigation extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;


    LinearLayout layoutBottomSheet;

    private LatLng mOriginCoordinates;
    private String mOriginLongName;
    private String mOriginCode;

    private LatLng mDestinationCoordinates;
    private String mDestinationLongName;
    private String mDestinationCode;

    private TravelMode mMode;

    private BottomSheetBehavior sheetBehavior;
    private RecyclerView mRecyclerView;
    private RouteAdapter mAdapter;
    private OutdoorDirectionsService mOutdoorDirectionService;
    private DirectionsResult mDirectionsFromOutdoors;

    private List<DirectionsStep> stepsList;

    GeoApiContext GAC;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Extract locations data
        Intent modeSelectIntent = getIntent();
        unpackIntent(modeSelectIntent);

        // Initialize the GeoAPI context
        GAC = new GeoApiContext.Builder()
                .apiKey(getString(R.string.Google_API_Key))
                .build();

        // Compute the directions, Update the view, and add the polylines
        computeDirections(mOriginCoordinates, mDestinationCoordinates, mMode);

        // Create the recycler view
        layoutBottomSheet = findViewById(R.id.bottom_sheet);
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.routes_recycler_view);
        stepsList = new ArrayList<>();
        mAdapter = new RouteAdapter(stepsList);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(mAdapter);


//        sheetBehavior = BottomSheetBehavior.from(layoutBottomSheet);
//
//        /**
//         * bottom sheet state change listener
//         * we are changing button text when sheet changed state
//         * */
//        sheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
//            @Override
//            public void onStateChanged(@NonNull View bottomSheet, int newState) {
//
//            }
//
//            @Override
//            public void onSlide(@NonNull View view, float v) {
//
//            }
//        });

    }


    public void unpackIntent(Intent intent) {
        mOriginCoordinates = intent.getParcelableExtra("fromCoordinates");
        mOriginLongName = intent.getStringExtra("fromLongName");
        mOriginCode = intent.getStringExtra("fromCode");

        mDestinationCoordinates = intent.getParcelableExtra("toCoordinates");
        mDestinationLongName = intent.getStringExtra("toLongName");
        mDestinationCode = intent.getStringExtra("toCode");

        mMode = intent.getParcelableExtra("mode");
    }

    // Sends Directions API request
    // Calls function to update the view elements on success
    public void computeDirections(LatLng origin, LatLng destination, TravelMode mode) {

        DirectionsApiRequest directions = new DirectionsApiRequest(GAC);

        directions.origin(new com.google.maps.model.LatLng(origin.latitude, origin.longitude));
        directions.destination(new com.google.maps.model.LatLng(destination.latitude, destination.longitude));
        directions.mode(mode);

        directions.setCallback(new PendingResult.Callback<DirectionsResult>() {

            @Override
            public void onResult(DirectionsResult result) {
                updateView(result);
                addPolyLinesToMap(result);
            }

            @Override
            public void onFailure(Throwable e) {
                Log.d("OUTDOORSERVICES", "Failed to get directions");
            }
        });
    }

    private void updateView(final DirectionsResult result) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                // create drawer and set behavior
                for (DirectionsStep step : result.routes[0].legs[0].steps) {
                    stepsList.add(step);
                }
                mAdapter.notifyDataSetChanged();
            }
        });
    }

    // polyline function - can be modified to work with a single route
    private void addPolyLinesToMap(final DirectionsResult result) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                for (DirectionsRoute route: result.routes) {
                    List<com.google.maps.model.LatLng> decodedPath = PolylineEncoding.decode(route.overviewPolyline.getEncodedPath());

                    List<LatLng> newDecodedPath = new ArrayList<>();
                    for (com.google.maps.model.LatLng latLng: decodedPath) {
                        newDecodedPath.add(new LatLng(latLng.lat, latLng.lng));
                    }

                    Polyline polyline = mMap.addPolyline(new PolylineOptions().addAll(newDecodedPath));
                }
            }
        });
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
        mMap = googleMap;

        mMap.addMarker(new MarkerOptions().position(mOriginCoordinates).title("Start of route"));
        mMap.addMarker(new MarkerOptions().position(mDestinationCoordinates).title("End of route"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mOriginCoordinates, 16f));
    }
}
