package us.koller.calendarwidget.widget

import android.appwidget.AppWidgetManager
import android.content.ContentUris
import android.content.Intent
import android.icu.text.SimpleDateFormat
import android.provider.CalendarContract
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import us.koller.calendarwidget.CalendarLoader
import us.koller.calendarwidget.CalendarLoaderImpl
import us.koller.calendarwidget.Event
import us.koller.calendarwidget.R
import us.koller.calendarwidget.util.Prefs
import us.koller.calendarwidget.util.SectionedRemoteViewsFactory
import java.util.*

/**
 * CalendarRemoteViewsService to create an instance of CalendarRemoteViewsFactory
 * */
class CalendarRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        /* retrieve widget id from */
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        /* new Preferences instance */
        val prefs = Prefs(applicationContext)
        /* create new factory */
        val factory = CalendarRemoteViewsFactory(
            applicationContext.packageName,
            CalendarLoaderImpl.wrap(applicationContext.contentResolver),
            prefs.getDaysShownInWidget(),
            prefs.loadWidgetPrefs(widgetId).calendarIds
        )
        /* loading string resources here, because access to applicationContext */
        factory.todayString = applicationContext.getString(R.string.today)
        factory.tomorrowString = applicationContext.getString(R.string.tomorrow)
        /* add section labels */
        /* return factory */
        return factory
    }
}

/**
 * CalendarRemoteViewsFactory: provide RemoteView for the ListView in the Widget
 * */
class CalendarRemoteViewsFactory(
    packageName: String,
    private val loader: CalendarLoader,
    private val daysShown: Int,
    private val selectedCalendars: List<Long>
) : SectionedRemoteViewsFactory<Event.Instance>(packageName) {

    var todayString = ""
    var tomorrowString = ""

    override fun onCreate() {
        /* nothing to create */
    }

    override fun onDestroy() {
        /* nothing to destroy */
    }

    override fun getLoadingView(): RemoteViews? {
        /* no loadingView needed */
        return null
    }

    override fun onDataSetChanged() {
        /* date formatter */
        val formatter = SimpleDateFormat("EEE, dd MMMM")
        /* get todays timestamp with 00:00:00:000 */
        val todayCal = Calendar.getInstance()
        todayCal.set(Calendar.HOUR_OF_DAY, 0)
        todayCal.set(Calendar.MINUTE, 0)
        todayCal.set(Calendar.SECOND, 0)
        todayCal.set(Calendar.MILLISECOND, 0)
        val todayTimestamp = todayCal.timeInMillis
        /* calculate today's and tomorrows date to replace by "Today" or "Tomorrow" string */
        val today = formatter.format(Date(todayTimestamp))
        val tomorrow = formatter.format(Date(todayTimestamp + 24 * 60 * 60 * 1000))
        /* use loader to load events */
        val events = loader.loadEventInstances(daysShown)
            /* filter out event from un-selected calendars */
            .filter { selectedCalendars.contains(it.event.calendarId) }
        /* set items on SectionedRemoteViewsFactory */
        setItems(events)
        /* add sections */
        (0..daysShown)
            /* map day to timestamp */
            .map { todayTimestamp + it * 24 * 60 * 60 * 1000 }
            /* find section index in list */
            .map { t ->
                val index = events.indexOfFirst { it.begin > t }
                when (index) {
                    /* no event on last day found */
                    -1 -> Pair(events.size, t)
                    else -> Pair(index, t)
                }
            }
            /* map timestamp to string */
            .map {
                val date = formatter.format(Date(it.second))
                Pair(
                    it.first, when (date) {
                        today -> todayString
                        tomorrow -> tomorrowString
                        else -> date
                    }
                )
            }
            /* add the sections */
            .forEach { addSection(it.first, it.second) }
    }

    override fun getItemId(item: Event.Instance): Long {
        /* use position as itemId */
        return item.event.id + item.begin
    }

    override fun getViewAt(item: Event.Instance): RemoteViews {
        /* create new RemoteViews instance */
        val views = RemoteViews(packageName, R.layout.event_item_view)
        /* bind Data */
        /* set colordot color */
        views.setInt(R.id.colordot, "setColorFilter", item.event.displayColor)
        /* set event start time */
        views.setTextViewText(
            R.id.event_start_time,
            when (item.event.allDay) {
                false -> SimpleDateFormat("HH:mm").format(Date(item.begin))
                else -> ""
            }
        )
        /* set event title */
        views.setTextViewText(R.id.event_title, item.event.title)

        /* set the fill-intent to pass data back to CalendarAppwidgetProvider */
        /* construct eventUri to open event in calendar app */
        val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, item.event.id)
        /* create fill-intent */
        val fillInIntent = Intent()
            /* put eventUri as extra */
            .putExtra(CalendarAppWidgetProvider.EVENT_URI_EXTRA, eventUri.toString())
            /* put begin and end extra to open specific instance */
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, item.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, item.end)
        /* set the fill-intent */
        views.setOnClickFillInIntent(R.id.event_card, fillInIntent)

        return views
    }
}