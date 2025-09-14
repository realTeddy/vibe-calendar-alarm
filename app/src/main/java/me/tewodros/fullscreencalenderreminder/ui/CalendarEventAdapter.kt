package me.tewodros.fullscreencalenderreminder.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import me.tewodros.fullscreencalenderreminder.databinding.ItemCalendarEventBinding
import me.tewodros.fullscreencalenderreminder.model.CalendarEvent

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
