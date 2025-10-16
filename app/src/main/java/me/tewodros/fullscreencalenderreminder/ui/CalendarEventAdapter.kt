package me.tewodros.vibecalendaralarm.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import me.tewodros.vibecalendaralarm.databinding.ItemCalendarEventBinding
import me.tewodros.vibecalendaralarm.model.CalendarEvent

/**
 * RecyclerView adapter for displaying calendar events
 * Uses ListAdapter for efficient updates with DiffUtil
 */
class CalendarEventAdapter : ListAdapter<CalendarEvent, CalendarEventAdapter.EventViewHolder>(
    EventDiffCallback(),
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemCalendarEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class EventViewHolder(
        private val binding: ItemCalendarEventBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        fun bind(event: CalendarEvent) {
            binding.eventTitle.text = event.title
            binding.eventTime.text = dateFormat.format(Date(event.startTime))

            // Update countdown text
            binding.countdownText.text = formatCountdown(event.startTime)

            // Update calendar name
            binding.calendarName.text = "📅 ${event.calendarName}"

            // Update reminder chip
            val reminderText = when {
                event.reminderMinutes.isEmpty() -> "No reminders"
                event.reminderMinutes.size == 1 -> "${event.reminderMinutes[0]} min before"
                else -> "${event.reminderMinutes.size} reminders"
            }
            binding.reminderChip.text = reminderText

            // Show/hide reminder chip based on if reminders exist
            binding.reminderChip.visibility = if (event.reminderMinutes.isNotEmpty()) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }

        /**
         * Format countdown to event start time (same as ReminderActivity)
         */
        private fun formatCountdown(eventStartTime: Long): String {
            val currentTime = System.currentTimeMillis()
            val diffMs = eventStartTime - currentTime

            return when {
                diffMs < 0 -> {
                    val pastMs = -diffMs
                    when {
                        pastMs < 60_000 -> "Started just now"
                        pastMs < 3600_000 -> {
                            val minutes = (pastMs / 60_000).toInt()
                            "Started $minutes minute${if (minutes == 1) "" else "s"} ago"
                        }
                        pastMs < 86400_000 -> {
                            val hours = (pastMs / 3600_000).toInt()
                            "Started $hours hour${if (hours == 1) "" else "s"} ago"
                        }
                        else -> {
                            val days = (pastMs / 86400_000).toInt()
                            "Started $days day${if (days == 1) "" else "s"} ago"
                        }
                    }
                }
                diffMs < 60_000 -> "Starting now"
                diffMs < 3600_000 -> {
                    val minutes = (diffMs / 60_000).toInt()
                    "Starting in $minutes minute${if (minutes == 1) "" else "s"}"
                }
                diffMs < 86400_000 -> {
                    val hours = (diffMs / 3600_000).toInt()
                    "Starting in $hours hour${if (hours == 1) "" else "s"}"
                }
                else -> {
                    val days = (diffMs / 86400_000).toInt()
                    "Starting in $days day${if (days == 1) "" else "s"}"
                }
            }
        }
    }
}

/**
 * DiffUtil callback for efficient RecyclerView updates
 */
class EventDiffCallback : DiffUtil.ItemCallback<CalendarEvent>() {

    override fun areItemsTheSame(oldItem: CalendarEvent, newItem: CalendarEvent): Boolean {
        return oldItem.id == newItem.id && oldItem.startTime == newItem.startTime
    }

    override fun areContentsTheSame(oldItem: CalendarEvent, newItem: CalendarEvent): Boolean {
        return oldItem == newItem
    }
}
