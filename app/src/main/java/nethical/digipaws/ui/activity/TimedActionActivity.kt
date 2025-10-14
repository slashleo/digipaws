package nethical.digipaws.ui.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nethical.digipaws.R
import nethical.digipaws.databinding.ActivityAddTimedActionActivityBinding
import nethical.digipaws.databinding.CheatHourItemBinding
import nethical.digipaws.databinding.DialogAddTimedActionBinding
import nethical.digipaws.services.AppBlockerService
import nethical.digipaws.utils.SavedPreferencesLoader
import nethical.digipaws.utils.TimeTools
import nl.joery.timerangepicker.TimeRangePicker

class TimedActionActivity : AppCompatActivity() {

    companion object {
        const val MODE_APP_BLOCKER_CHEAT_HOURS = 1
        const val MODE_AUTO_FOCUS = 2
    }

    private lateinit var binding: ActivityAddTimedActionActivityBinding
    private val savedPreferencesLoader = SavedPreferencesLoader(this)
    private var timedActionList: MutableList<AutoTimedActionItem> = mutableListOf()

    private lateinit var selectUnblockedAppsLauncher: ActivityResultLauncher<Intent>
    private var selectedUnblockedApps: ArrayList<String>? = arrayListOf()

    private lateinit var dialogAddToTimedActionBinding: DialogAddTimedActionBinding

    private var selectedMode = MODE_APP_BLOCKER_CHEAT_HOURS
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityAddTimedActionActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (intent.hasExtra("selected_mode")) {
            selectedMode = intent.getIntExtra("selected_mode", MODE_APP_BLOCKER_CHEAT_HOURS)
        }

        when (selectedMode) {
            MODE_APP_BLOCKER_CHEAT_HOURS -> {
                timedActionList = savedPreferencesLoader.loadAppBlockerCheatHoursList()
            }

            MODE_AUTO_FOCUS -> timedActionList = savedPreferencesLoader.loadAutoFocusHoursList()
        }
        selectUnblockedAppsLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val selectedApps = result.data?.getStringArrayListExtra("SELECTED_APPS")
                    selectedApps?.let {
                        selectedUnblockedApps = selectedApps
                        dialogAddToTimedActionBinding.btnSelectUnblockedApps.text =
                            getString(R.string.app_s_selected, selectedApps.size)
                    }
                }
            }

        binding.recyclerView2.layoutManager = LinearLayoutManager(this)
        binding.recyclerView2.adapter = CheatHourAdapter(timedActionList) { item ->
            val position = timedActionList.indexOf(item)
            makeCheatHoursDialog(item, position)
        }

        binding.button.setOnClickListener {
            makeCheatHoursDialog()
        }
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun makeCheatHoursDialog(
        itemToEdit: AutoTimedActionItem? = null,
        itemPosition: Int? = null
    ) {
        dialogAddToTimedActionBinding = DialogAddTimedActionBinding.inflate(layoutInflater)

        var startTimeInMins: Int = TimeRangePicker.Time(6, 30).totalMinutes
        var endTimeInMins: Int = TimeRangePicker.Time(22, 0).totalMinutes

        selectedUnblockedApps = ArrayList()

        if (itemToEdit != null) {
            dialogAddToTimedActionBinding.cheatHourTitle.setText(itemToEdit.title)
            startTimeInMins = itemToEdit.startTimeInMins
            endTimeInMins = itemToEdit.endTimeInMins
            selectedUnblockedApps = ArrayList(itemToEdit.packages)
        }

        dialogAddToTimedActionBinding.picker.hourFormat = TimeRangePicker.HourFormat.FORMAT_24
        dialogAddToTimedActionBinding.picker.startTimeMinutes = startTimeInMins
        dialogAddToTimedActionBinding.picker.endTimeMinutes = endTimeInMins

        dialogAddToTimedActionBinding.fromTime.text =
            TimeRangePicker.Time(startTimeInMins / 60, startTimeInMins % 60).toString()
        dialogAddToTimedActionBinding.endTime.text =
            TimeRangePicker.Time(endTimeInMins / 60, endTimeInMins % 60).toString()

        dialogAddToTimedActionBinding.picker.setOnTouchListener { v, event ->
            // Disable ScrollView's touch interception when interacting with the picker
            when (event.action) {
                MotionEvent.ACTION_DOWN -> dialogAddToTimedActionBinding.scrollview.requestDisallowInterceptTouchEvent(
                    true
                )

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dialogAddToTimedActionBinding.scrollview.requestDisallowInterceptTouchEvent(
                    false
                )
            }
            v.onTouchEvent(event) // Pass the event to the picker
        }
        dialogAddToTimedActionBinding.picker.setOnTimeChangeListener(object :
            TimeRangePicker.OnTimeChangeListener {
            override fun onStartTimeChange(startTime: TimeRangePicker.Time) {
                dialogAddToTimedActionBinding.fromTime.text =
                    dialogAddToTimedActionBinding.picker.startTime.toString()
                startTimeInMins = dialogAddToTimedActionBinding.picker.startTimeMinutes
            }

            override fun onEndTimeChange(endTime: TimeRangePicker.Time) {
                dialogAddToTimedActionBinding.endTime.text =
                    dialogAddToTimedActionBinding.picker.endTime.toString()
                endTimeInMins = dialogAddToTimedActionBinding.picker.endTimeMinutes
            }

            override fun onDurationChange(duration: TimeRangePicker.TimeDuration) {
            }
        })

        when (selectedMode) {
            MODE_AUTO_FOCUS -> {
                dialogAddToTimedActionBinding.timedTitle.text = "Specify Auto-Focus Hours"
                if (itemToEdit != null) {
                    dialogAddToTimedActionBinding.btnSelectUnblockedApps.text =
                        getString(R.string.app_s_selected, itemToEdit.packages.size)
                } else {
                    dialogAddToTimedActionBinding.btnSelectUnblockedApps.text =
                        "Select Apps to Block"
                }
            }

            MODE_APP_BLOCKER_CHEAT_HOURS -> {
                dialogAddToTimedActionBinding.timedTitle.text = "Specify Cheat Hours"
                dialogAddToTimedActionBinding.timedTitle.text = "Specify Apps to Unblock"
            }
        }

        dialogAddToTimedActionBinding.btnSelectUnblockedApps.setOnClickListener {
            val intent = Intent(this, SelectAppsActivity::class.java)
            intent.putStringArrayListExtra(
                "PRE_SELECTED_APPS",
                selectedUnblockedApps
            )

            if (selectedMode == MODE_APP_BLOCKER_CHEAT_HOURS) {
                intent.putStringArrayListExtra(
                    "APP_LIST",
                    ArrayList(savedPreferencesLoader.loadBlockedApps())
                )
            }

            selectUnblockedAppsLauncher.launch(intent)
        }

        val positiveButtonText = if (itemToEdit != null) {
            getString(R.string.save)
        } else {
            getString(R.string.add)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogAddToTimedActionBinding.root)
            .setPositiveButton(positiveButtonText, null)
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = dialogAddToTimedActionBinding.cheatHourTitle.text?.toString()?.trim()
            val apps = selectedUnblockedApps

            when {
                title.isNullOrEmpty() -> {
                    Toast.makeText(
                        this,
                        getString(R.string.please_type_a_title),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                apps.isNullOrEmpty() -> {
                    Toast.makeText(
                        this,
                        getString(R.string.please_select_a_few_apps),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                else -> {
                    val newItem = AutoTimedActionItem(
                        title,
                        startTimeInMins,
                        endTimeInMins,
                        apps
                    )

                    if (itemPosition != null) {
                        timedActionList[itemPosition] = newItem
                        binding.recyclerView2.adapter?.notifyItemChanged(itemPosition)
                    } else {
                        timedActionList.add(newItem)
                        binding.recyclerView2.adapter?.notifyItemInserted(timedActionList.size - 1)
                    }

                    saveList()
                    dialog.dismiss()
                }
            }
        }
    }


    inner class CheatHourAdapter(
        private val items: List<AutoTimedActionItem>,
        private val onItemClick: (AutoTimedActionItem) -> Unit
    ) : RecyclerView.Adapter<CheatHourAdapter.CheatHourViewHolder>() {

        inner class CheatHourViewHolder(
            private val binding: CheatHourItemBinding
        ) :
            RecyclerView.ViewHolder(binding.root) {

            private val cardView: MaterialCardView =
                itemView.findViewById(R.id.cheat_hour_card_view)

            @SuppressLint("SetTextI18n")
            fun bind(item: AutoTimedActionItem) {
                binding.cheatHourTitle.text = item.title
                val convertedStartTime = TimeTools.convertMinutesTo24Hour(item.startTimeInMins)
                val convertedEndTIme = TimeTools.convertMinutesTo24Hour(item.endTimeInMins)

                binding.removeCheatHour.setOnClickListener {
                    timedActionList.removeAt(layoutPosition)
                    notifyItemRemoved(layoutPosition)
                    saveList()
                }

                binding.cheatTimings.text =
                    getString(
                        R.string.cheat_timings,
                        convertedStartTime.first,
                        convertedStartTime.second,
                        convertedEndTIme.first,
                        convertedEndTIme.second
                    )
                item.packages.forEach { packageName ->
                    binding.selectedApps.text =
                        binding.selectedApps.text.toString() + " " + packageName
                }

                cardView.setOnClickListener {
                    onItemClick(item)
                }

            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CheatHourViewHolder {
            val binding = CheatHourItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return CheatHourViewHolder(binding)
        }

        override fun onBindViewHolder(holder: CheatHourViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }

    private fun saveList() {
        when (selectedMode) {
            MODE_APP_BLOCKER_CHEAT_HOURS -> {
                savedPreferencesLoader.saveAppBlockerCheatHoursList(timedActionList)
                sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_APP_BLOCKER))
            }

            MODE_AUTO_FOCUS -> {
                savedPreferencesLoader.saveAutoFocusHoursList(timedActionList)
                sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE))
            }
        }
    }

    data class AutoTimedActionItem(
        val title: String,
        val startTimeInMins: Int,
        val endTimeInMins: Int,
        val packages: ArrayList<String>,
        val isProceedHidden: Boolean = false
    )


}