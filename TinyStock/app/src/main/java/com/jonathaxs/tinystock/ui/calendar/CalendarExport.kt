package com.jonathaxs.tinystock.ui.calendar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.model.OrderCalendarEventDraft
import com.jonathaxs.tinystock.core.model.OrderCalendarEventKind
import com.jonathaxs.tinystock.core.model.SalesChannel
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Abre o editor de evento da agenda do aparelho ja preenchido, sem pedir permissao (a pessoa confirma la).
 * Equivale ao `CalendarEventEditView` do iOS; titulo e notas seguem o `OrderCalendarExportPlanner` do iOS.
 */
internal object CalendarExport {

    fun open(context: Context, draft: OrderCalendarEventDraft, zone: ZoneId) {
        // Evento de dia inteiro: a agenda espera a meia-noite em UTC do dia, nao a do fuso local.
        val day = draft.startDate.atZone(zone).toLocalDate()
        val begin = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title(context, draft))
            .putExtra(CalendarContract.Events.DESCRIPTION, notes(context, draft))
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.calendar_export_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun title(context: Context, draft: OrderCalendarEventDraft): String {
        val name = draft.primaryProductName ?: context.getString(R.string.calendar_export_subject_fallback)
        val subject = if (draft.additionalProductCount > 0) {
            context.getString(R.string.calendar_export_subject_multiple, name, draft.additionalProductCount)
        } else {
            name
        }
        val format = when (draft.kind) {
            OrderCalendarEventKind.Production -> R.string.calendar_export_production_title
            OrderCalendarEventKind.Shipping -> R.string.calendar_export_shipping_title
        }
        return context.getString(format, subject)
    }

    private fun notes(context: Context, draft: OrderCalendarEventDraft): String = buildList {
        add(context.getString(R.string.calendar_export_notes_store, draft.storeName))
        if (draft.buyerName.isNotEmpty()) add(context.getString(R.string.calendar_export_notes_buyer, draft.buyerName))
        if (draft.externalReference.isNotEmpty()) {
            add(context.getString(R.string.calendar_export_notes_reference, draft.externalReference))
        }
        val channel = draft.customChannelDisplayName ?: context.getString(
            when (draft.channel) {
                SalesChannel.Direct -> R.string.order_channel_direct
                SalesChannel.Shopee -> R.string.order_channel_shopee
                SalesChannel.MercadoLivre -> R.string.order_channel_mercadoLivre
                SalesChannel.Other -> R.string.order_channel_other
                null -> R.string.order_channel_unknown
            },
        )
        add(context.getString(R.string.calendar_export_notes_channel, channel))
    }.joinToString("\n")
}
