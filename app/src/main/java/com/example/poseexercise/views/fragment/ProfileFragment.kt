package com.example.poseexercise.views.fragment

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.poseexercise.R
import com.example.poseexercise.data.results.WorkoutResult
import com.example.poseexercise.util.MemoryManagement
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate

/**
 * Profile view with the information on workout for a week
 */
class ProfileFragment : Fragment(), MemoryManagement {
    private lateinit var pieChart: PieChart
    private var workoutResults: List<WorkoutResult>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        // Initialize the PieChart
        pieChart = view.findViewById(R.id.pie_chart)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Call the function to set up the PieChart
        setPieChart()
    }

    private fun setPieChart() {
        // Set up the PieChart
        pieChart.setUsePercentValues(true)
        pieChart.description.isEnabled = false
        pieChart.isRotationEnabled = false
        pieChart.centerText = "진단 결과"
        pieChart.setCenterTextSize(20f)
        pieChart.animateY(1400)

        // Prepare data for the PieChart
        val entries = ArrayList<PieEntry>()
        entries.add(PieEntry(14f, "Apple"))
        entries.add(PieEntry(22f, "Orange"))
        entries.add(PieEntry(7f, "Mango"))
        entries.add(PieEntry(31f, "RedOrange"))
        entries.add(PieEntry(26f, "Other"))

        val colorsItems = ArrayList<Int>()
        for (c in ColorTemplate.VORDIPLOM_COLORS) colorsItems.add(c)
        for (c in ColorTemplate.JOYFUL_COLORS) colorsItems.add(c)
        for (c in ColorTemplate.COLORFUL_COLORS) colorsItems.add(c)
        for (c in ColorTemplate.LIBERTY_COLORS) colorsItems.add(c)
        for (c in ColorTemplate.PASTEL_COLORS) colorsItems.add(c)
        colorsItems.add(ColorTemplate.getHoloBlue())

        val pieDataSet = PieDataSet(entries, "")
        pieDataSet.colors = colorsItems
        pieDataSet.valueTextColor = Color.BLACK
        pieDataSet.valueTextSize = 18f

        val pieData = PieData(pieDataSet)
        pieChart.data = pieData
    }

    override fun clearMemory() {
        // Clear memory if necessary
        workoutResults = null
    }

    override fun onDestroy() {
        clearMemory()
        super.onDestroy()
    }
}
