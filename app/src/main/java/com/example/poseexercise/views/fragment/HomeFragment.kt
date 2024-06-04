package com.example.poseexercise.views.fragment
import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.poseexercise.R
import com.example.poseexercise.backgroundlocationtracking.LocationService // 추가된 부분
import com.example.poseexercise.data.database.AppRepository
import com.example.poseexercise.data.plan.Plan
import com.example.poseexercise.data.results.RecentActivityItem
import com.example.poseexercise.data.results.WorkoutResult
import com.example.poseexercise.util.MemoryManagement
import com.example.poseexercise.util.MyApplication
import com.example.poseexercise.util.MyUtils
import com.example.poseexercise.viewmodels.HomeViewModel
import com.example.poseexercise.viewmodels.ResultViewModel
import com.google.android.gms.maps.CameraUpdateFactory // 추가된 부분
import com.google.android.gms.maps.GoogleMap // 추가된 부분
import com.google.android.gms.maps.SupportMapFragment // 추가된 부분
import com.google.android.gms.maps.model.LatLng // 추가된 부분
import com.google.android.gms.maps.model.MarkerOptions // 추가된 부분
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import kotlin.math.min

class HomeFragment : Fragment(R.layout.fragment_home), /*PlanAdapter.ItemListener,*/ MemoryManagement, LocationListener { // 변경된 부분
    @Suppress("PropertyName")
    private val TAG = "DriveGuardian Home Fragment"
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var resultViewModel: ResultViewModel
    //private lateinit var recentActivityRecyclerView: RecyclerView
    // private lateinit var recentActivityAdapter: RecentActivityAdapter
    private var planList: List<Plan>? = emptyList()
    private var notCompletePlanList: MutableList<Plan>? = Collections.emptyList()
    // private var today: String = DateFormat.format("EEEE", Date()) as String
    private lateinit var progressText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var noPlanTV: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressPercentage: TextView
    private var workoutResults: List<WorkoutResult>? = null
    private lateinit var appRepository: AppRepository
    // private lateinit var addPlanViewModel: AddPlanViewModel
    // private lateinit var adapter: PlanAdapter

    private var googleMap: GoogleMap? = null // 추가된 부분
    private var myPosition: LatLng? = null // 추가된 부분
    private lateinit var locationUpdateReceiver: BroadcastReceiver // 추가된 부분


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Initialize RecyclerView and its adapter for recent activity
//        progressText = view.findViewById(R.id.exercise_left)
        // recyclerView = view.findViewById(R.id.today_plans)
        // recentActivityRecyclerView = view.findViewById(R.id.recentActivityRecyclerView)
        // recentActivityAdapter = RecentActivityAdapter(emptyList())
        // recentActivityRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        // recentActivityRecyclerView.adapter = recentActivityAdapter
        // noPlanTV = view.findViewById(R.id.no_plan)
//        progressBar = view.findViewById(R.id.progress_bar)
//        progressPercentage = view.findViewById(R.id.progress_text)
        appRepository = AppRepository(requireActivity().application)
        // Initialize ViewModel
        resultViewModel = ResultViewModel(MyApplication.getInstance())
        // addPlanViewModel = AddPlanViewModel(MyApplication.getInstance())
        lifecycleScope.launch {
            val workoutResults = resultViewModel.getRecentWorkout()
            // Call the function to load data and set up the chart
            loadDataAndSetupChart()
            // Transform WorkoutResult objects into RecentActivityItem objects
            val imageResources = arrayOf(R.drawable.blue, R.drawable.green, R.drawable.orange)
            // Transform WorkoutResult objects into RecentActivityItem objects
            val recentActivityItems = workoutResults?.mapIndexed { index, it ->
                RecentActivityItem(
                    imageResId = imageResources[index % imageResources.size],
                    exerciseType = MyUtils.exerciseNameToDisplay(it.exerciseName),
                    reps = "${it.repeatedCount} reps"
                )
            }
            /*
            // Update the adapter with the transformed data
             recentActivityAdapter.updateData(recentActivityItems ?: emptyList())
            // Check if the recentActivityItems list is empty
            if (recentActivityItems.isNullOrEmpty()) {
                recentActivityRecyclerView.isVisible = false
                // Show a message or handle the empty case as per your UI requirements
                val noActivityMessage = view.findViewById<TextView>(R.id.no_activity_message)
                noActivityMessage.text = getString(R.string.no_activities_yet)
                noActivityMessage.isVisible = true
            } else {
                recentActivityRecyclerView.isVisible = true
            }
             */
        }
        // Initialize home view model, RecyclerView and its adapter for today's plans
        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        // get the list of plans from database
        lifecycleScope.launch(Dispatchers.IO) {
            //val result1 = withContext(Dispatchers.IO) { homeViewModel.getPlanByDay(today) }
            //val result2 = withContext(Dispatchers.IO) { homeViewModel.getNotCompletePlans(today) }
            withContext(Dispatchers.Main) {
                // updateResultFromDatabase(result1, result2)
            }
        }

        initializeMap() // 추가된 부분
        clickStart() // 추가된 부분
        clickStop() // 추가된 부분

        // BroadcastReceiver 등록 // 추가된 부분
        locationUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val latitude = intent?.getDoubleExtra("latitude", 0.0) ?: return
                val longitude = intent.getDoubleExtra("longitude", 0.0)
                Log.d("HomeFragment", "Received location update: $latitude, $longitude")
                updateMapLocation(latitude, longitude)
            }
        }
        requireActivity().registerReceiver(locationUpdateReceiver, IntentFilter("LOCATION_UPDATE"))
    }

    // 추가된 메서드
    private fun initializeMap() {
        val fm = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        fm.getMapAsync { map ->
            googleMap = map
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // 권한 요청
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
                return@getMapAsync
            }
            googleMap?.isMyLocationEnabled = true

            // 이화여자대학교의 좌표를 설정
            val ewhaPosition = LatLng(37.5610, 126.9468)
            myPosition = ewhaPosition

            // 이화여자대학교에 마커 추가 및 카메라 이동
            myPosition?.let {
                googleMap?.addMarker(MarkerOptions().position(it).title("Ewha Womans University"))
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 18f))
            }

            // 지도 긴 클릭 이벤트 설정
            setMapLongClick(googleMap)

            // 로그 추가
            Log.d("HomeFragment", "Map initialized with Ewha Womans University")
        }
    }

    // 지도 긴 클릭 이벤트 설정 메서드
    private fun setMapLongClick(map: GoogleMap?) {
        map?.setOnMapLongClickListener { latLng ->
            val snippet = String.format(
                Locale.getDefault(),
                "Lat: %1$.5f, Long: %2$.5f",
                latLng.latitude,
                latLng.longitude
            )
            map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Dropped Pin")
                    .snippet(snippet)
            )
        }
    }

    // 추가된 메서드
    override fun onLocationChanged(location: Location) {
        location.let {
            val latitude: Double = it.latitude
            val longitude: Double = it.longitude
            myPosition = LatLng(latitude, longitude)
            myPosition?.let {
                googleMap?.clear()
                googleMap?.addMarker(MarkerOptions().position(it).title("Current Location"))
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 18f))
            }

            // 로그 추가
            Log.d("HomeFragment", "Location changed: $latitude, $longitude")
        }
    }

    private fun updateMapLocation(latitude: Double, longitude: Double) { // 추가된 메서드
        myPosition = LatLng(latitude, longitude)
        myPosition?.let {
            googleMap?.clear()
            googleMap?.addMarker(MarkerOptions().position(it).title("Current Location"))
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 18f))
            Log.d("HomeFragment", "Location changed: $latitude, $longitude")
        }
    }

    // 추가된 메서드
    private fun clickStart() {
        val clickStart = view?.findViewById<Button>(R.id.start_button)
        clickStart?.setOnClickListener {
            Log.d("HomeFragment", "Start button clicked")
            val intent = Intent(requireContext(), LocationService::class.java).apply {
                action = LocationService.ACTION_START
                requireContext().startService(this)
            }
        }
    }

    // 추가된 메서드
    private fun clickStop() {
        val clickStop = view?.findViewById<Button>(R.id.stop_button)
        clickStop?.setOnClickListener {
            val intent = Intent(requireContext(), LocationService::class.java).apply {
                action = LocationService.ACTION_STOP
                requireContext().startService(this)
            }
        }
    }

    // 기존 메서드
    private fun loadDataAndSetupChart() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Fetch workout results asynchronously
            workoutResults = resultViewModel.getAllResult()
            // Filter workout results for today
            val todayWorkoutResults = workoutResults?.filter {
                isToday(it.timestamp)
            }
            /*
            // Observe exercise plans from the database
            withContext(Dispatchers.Main) {
                appRepository.allPlans.observe(viewLifecycleOwner) { exercisePlans ->
                    // Filter exercise plans for today
                    val todayExercisePlans =
                        exercisePlans?.filter { it.selectedDays.contains(today) }

                    // Calculate progress and update UI
                    val totalPlannedRepetitions = todayExercisePlans?.sumOf { it.repeatCount } ?: 0
                    val totalCompletedRepetitions =
                        todayWorkoutResults?.sumOf { it.repeatedCount } ?: 0
                    val progressPercentage = if (totalPlannedRepetitions != 0) {
                        ((totalCompletedRepetitions.toDouble() / totalPlannedRepetitions) * 100).toInt()
                    } else {
                        0
                    }
                    // Update the ProgressBar and TextView with the progress percentage
                    updateProgressViews(progressPercentage)

                }
            }
             */
        }
    }

    // 기존 메서드
    private fun updateProgressViews(progress: Int) {
        // Check if progressPercentage is greater than 0
        if (progress > 0) {
            // Update progress views (ProgressBar and TextView)
            val cappedProgress = min(progress, 100)
            progressBar.progress = cappedProgress
            progressPercentage.text = String.format("%d%%", cappedProgress)
        } else {
            // If progressPercentage is 0 or less, hide the progress views
            progressBar.visibility = View.GONE
            progressText.visibility = View.GONE
        }
    }

    // 기존 메서드
    private fun isToday(s: Long, locale: Locale = Locale.getDefault()): Boolean {
        return try {
            val sdf = SimpleDateFormat("MM/dd/yyyy", locale)
            val netDate = Date(s)
            val currentDate = sdf.format(Date())
            sdf.format(netDate) == currentDate
        } catch (e: Exception) {
            false
        }
    }

    // 기존 메서드
    override fun clearMemory() {
        planList = null
        notCompletePlanList = null
        workoutResults = null
    }

    // 기존 메서드
    override fun onDestroy() {
        clearMemory()
        super.onDestroy()
    }

    override fun onDestroyView() { // 추가된 메서드
        super.onDestroyView()
        requireActivity().unregisterReceiver(locationUpdateReceiver)
    }

    override fun onProviderEnabled(provider: String) {
    }

    override fun onProviderDisabled(provider: String) {
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1 // 추가된 부분
    }
}
