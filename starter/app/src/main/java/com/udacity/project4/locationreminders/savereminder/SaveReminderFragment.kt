package com.udacity.project4.locationreminders.savereminder

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import com.udacity.project4.BuildConfig
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSaveReminderBinding
import com.udacity.project4.locationreminders.geofence.GeofenceHelper
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import org.koin.android.ext.android.inject

class SaveReminderFragment : BaseFragment() {
    private lateinit var geofenceHelper: GeofenceHelper
    private lateinit var geofencingClient:GeofencingClient

    //Get the view model this time as a single to be shared with the another fragment
    override val _viewModel: SaveReminderViewModel by inject()
    private lateinit var binding: FragmentSaveReminderBinding

    private val runningQOrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    private val runningROrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_save_reminder, container, false)

        setDisplayHomeAsUpEnabled(true)

        binding.viewModel = _viewModel

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Initialized for Geofencing
        geofenceHelper= GeofenceHelper(requireActivity())
        geofencingClient=LocationServices.getGeofencingClient(requireActivity())


        binding.lifecycleOwner = this
        binding.selectLocation.setOnClickListener {
            if (isForegroundAndBackgroundPermissionGranted()) {
                if (isLocationEnabled()) {
                    //Navigate to another fragment to get the user location
                    _viewModel.navigationCommand.value =
                        NavigationCommand.To(SaveReminderFragmentDirections.actionSaveReminderFragmentToSelectLocationFragment())
                } else {
                    checkDeviceLocationSettings()
                }
            } else {
                requestForegroundAndBackgroundPermission()
            }
        }

        binding.saveReminder.setOnClickListener {
            val title = _viewModel.reminderTitle.value
            val description = _viewModel.reminderDescription.value
            val location = _viewModel.reminderSelectedLocationStr.value
            val latitude = _viewModel.latitude.value
            val longitude = _viewModel.longitude.value

            val reminderDataItem = ReminderDataItem(
                title, description, location, latitude, longitude
            )

            if (isForegroundAndBackgroundPermissionGranted()) {
                if (isLocationEnabled()) {
                    //TODO: use the user entered reminder details to:
                    // 1) DONE: add a geofencing request
                    // 2) DONE: save the reminder to the local db

                    if(_viewModel.validateEnteredData(reminderDataItem)){
                        //Navigate to another fragment to get the user location
                        _viewModel.validateAndSaveReminder(reminderDataItem)
                        addGeofence(reminderDataItem.id,LatLng(latitude!!,longitude!!))
                    }
                } else {
                    checkDeviceLocationSettings()
                }
            } else {
                requestForegroundAndBackgroundPermission()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun addGeofence(geofenceId:String, latlng:LatLng) {
        val geofence = geofenceHelper.getGeofence(
            geofenceId,
            latlng,
            200f,
            Geofence.GEOFENCE_TRANSITION_ENTER /*or Geofence.GEOFENCE_TRANSITION_DWELL or Geofence.GEOFENCE_TRANSITION_EXIT*/
        )
        val geofencingRequest: GeofencingRequest =
            geofenceHelper.geoFencingRequest(geofence)
        val pendingIntent = geofenceHelper.getPendingIntent()
        geofencingClient.addGeofences(geofencingRequest, pendingIntent!!)
            .addOnSuccessListener {
                Log.d(TAG, "onSuccess: Geofence Added ..")
            }
            .addOnFailureListener {
                val errorMessage = geofenceHelper.getErrorString(it)
                Log.d(TAG, "onFailure: $errorMessage")
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        //make sure to clear the view model after destroy, as it's a single view model.
        _viewModel.onClear()
    }


    override fun onStart() {
        super.onStart()
        checkPermissionsAndStartGeofencing()
    }

    /**
     * Starts the permission check and Geofence process only if the Geofence associated with the
     * current hint isn't yet active.
     */
    private fun checkPermissionsAndStartGeofencing() {
        //if (viewModel.geofenceIsActive()) return
        if (isForegroundAndBackgroundPermissionGranted()) {
            checkDeviceLocationSettingsAndStartGeofence()
        } else {
            requestForegroundAndBackgroundPermission()
        }
    }

    @SuppressLint("InlinedApi")
    private fun requestForegroundAndBackgroundPermission() {
        // DONE: Step 4 add code to request foreground and background permissions
        if (isForegroundAndBackgroundPermissionGranted())
            return

        var permissionArray = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val resultCode = when {
            runningROrLater->{
                requireActivity().checkBackgroundLocationPermissionAPI30(REQUEST_BACKGROUND_PERMISSION_REQUEST_CODE)
                REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
            }
            runningQOrLater -> {
                permissionArray += Manifest.permission.ACCESS_BACKGROUND_LOCATION
                REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_REQUEST_CODE
            }
            else -> REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
        }

        ActivityCompat.requestPermissions(
            requireActivity(),
            permissionArray,
            resultCode
        )

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // DONE: Step 5 add code to handle the result of the user's permission
        Log.d(SaveReminderFragment.TAG, "onRequestPermissionResult")
        if (grantResults.isEmpty() ||
            grantResults[LOCATION_PERMISSION_INDEX] == PackageManager.PERMISSION_DENIED ||
            (requestCode == REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_REQUEST_CODE && grantResults[BACKGROUND_LOCATION_PERMISSION_INDEX] == PackageManager.PERMISSION_DENIED)
        ) {
            Snackbar.make(
                binding.rootLayout,
                R.string.permission_denied_explanation,
                Snackbar.LENGTH_INDEFINITE
            ).setAction(R.string.settings) {
                startActivity(Intent().apply {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }.show()
        } else {
            checkDeviceLocationSettingsAndStartGeofence()
        }
    }


    /*
*  Uses the Location Client to check the current state of location settings, and gives the user
*  the opportunity to turn on location services within our app.
*/
    private fun checkDeviceLocationSettings(resolve: Boolean = true) {
        // DONE: Step 6 add code to check that the device's location is on
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_LOW_POWER
        }
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)

        val settingsClient = LocationServices.getSettingsClient(requireActivity())

        val locationSettingsResponseTask =
            settingsClient.checkLocationSettings(builder.build())

        locationSettingsResponseTask.addOnFailureListener { exception ->
            if (exception is ResolvableApiException && resolve) {
                try {
                    exception.startResolutionForResult(
                        requireActivity(),
                        REQUEST_TURN_DEVICE_LOCATION_ON
                    )
                } catch (sendEx: IntentSender.SendIntentException) {
                    Log.d(
                        SaveReminderFragment.TAG,
                        "Error getting location settings resolution: " + sendEx.message
                    )
                }
            } else {
                Snackbar.make(
                    binding.rootLayout,
                    R.string.location_required_error, Snackbar.LENGTH_INDEFINITE
                ).setAction(android.R.string.ok) {
                    checkDeviceLocationSettingsAndStartGeofence()
                }.show()
            }
        }

        locationSettingsResponseTask.addOnCompleteListener {
            if (it.isSuccessful) {
                /*//Navigate to another fragment to get the user location
                _viewModel.navigationCommand.value =
                    NavigationCommand.To(SaveReminderFragmentDirections.actionSaveReminderFragmentToSelectLocationFragment())*/
            }
        }
    }


    /*
  *  Uses the Location Client to check the current state of location settings, and gives the user
  *  the opportunity to turn on location services within our app.
  */
    private fun checkDeviceLocationSettingsAndStartGeofence(resolve: Boolean = true) {
        // DONE: Step 6 add code to check that the device's location is on
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_LOW_POWER
        }
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)

        val settingsClient = LocationServices.getSettingsClient(requireActivity())

        val locationSettingsResponseTask =
            settingsClient.checkLocationSettings(builder.build())

        locationSettingsResponseTask.addOnFailureListener { exception ->
            if (exception is ResolvableApiException && resolve) {
                try {
                    exception.startResolutionForResult(
                        requireActivity(),
                        REQUEST_TURN_DEVICE_LOCATION_ON
                    )
                } catch (sendEx: IntentSender.SendIntentException) {
                    Log.d(
                        SaveReminderFragment.TAG,
                        "Error getting location settings resolution: " + sendEx.message
                    )
                }
            } else {
                Snackbar.make(
                    binding.rootLayout,
                    R.string.location_required_error, Snackbar.LENGTH_INDEFINITE
                ).setAction(android.R.string.ok) {
                    checkDeviceLocationSettingsAndStartGeofence()
                }.show()
            }
        }

        locationSettingsResponseTask.addOnCompleteListener {
            if (it.isSuccessful) {
                //addGeofenceForClue()

            }
        }
    }

    @SuppressLint("InlinedApi")
    private fun isForegroundAndBackgroundPermissionGranted(): Boolean {
        val foregroundLocationApproved = (
                PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(
                    requireActivity(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                )
        val backgroundPermissionApproved =
            if (runningQOrLater) {
                PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(
                    requireActivity(),
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            } else {
                true
            }
        return foregroundLocationApproved && backgroundPermissionApproved

    }


    @TargetApi(30)
    private fun Context.checkBackgroundLocationPermissionAPI30(backgroundLocationRequestCode: Int) {
        if (checkSinglePermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) return
        AlertDialog.Builder(this)
            .setTitle(R.string.background_location_permission_title)
            .setMessage(R.string.background_location_permission_message)
            .setPositiveButton(R.string.yes) { _,_ ->
                // this request will take user to Application's Setting page
                requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), backgroundLocationRequestCode)
            }
            .setNegativeButton(R.string.no) { dialog,_ ->
                dialog.dismiss()
            }
            .create()
            .show()

    }

    private fun Context.checkSinglePermission(permission: String) : Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }



    private fun isLocationEnabled(): Boolean {
        val locationManager =
            requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    companion object {
        private val TAG = SaveReminderFragment::class.java.simpleName
        private const val REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_REQUEST_CODE = 33
        private const val REQUEST_BACKGROUND_PERMISSION_REQUEST_CODE = 35
        private const val REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 34
        private const val REQUEST_TURN_DEVICE_LOCATION_ON = 29
        private const val LOCATION_PERMISSION_INDEX = 0
        private const val BACKGROUND_LOCATION_PERMISSION_INDEX = 1
    }

}
