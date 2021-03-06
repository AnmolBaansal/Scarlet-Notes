package com.maubis.scarlet.base.note

import android.content.Context
import android.content.Intent
import android.support.v4.graphics.ColorUtils
import com.github.bijoysingh.starter.util.DateFormatter
import com.google.gson.Gson
import com.maubis.scarlet.base.config.CoreConfig
import com.maubis.scarlet.base.core.database.room.note.Note
import com.maubis.scarlet.base.core.database.room.tag.Tag
import com.maubis.scarlet.base.core.format.FormatType
import com.maubis.scarlet.base.core.note.NoteState
import com.maubis.scarlet.base.core.note.getFormats
import com.maubis.scarlet.base.core.note.getTagUUIDs
import com.maubis.scarlet.base.main.sheets.EnterPincodeBottomSheet
import com.maubis.scarlet.base.note.creation.activity.CreateNoteActivity
import com.maubis.scarlet.base.note.creation.activity.INTENT_KEY_DISTRACTION_FREE
import com.maubis.scarlet.base.note.creation.activity.INTENT_KEY_NOTE_ID
import com.maubis.scarlet.base.note.creation.activity.ViewAdvancedNoteActivity
import com.maubis.scarlet.base.support.database.tagsDB
import com.maubis.scarlet.base.support.ui.ThemedActivity
import com.maubis.scarlet.base.utils.removeMarkdownHeaders
import com.maubis.scarlet.base.utils.renderMarkdown
import java.util.*

fun Note.log(context: Context): String {
  val log = HashMap<String, Any>()
  log["note"] = this
  log["_title"] = getTitle()
  log["_text"] = getText()
  log["_image"] = getImageFile()
  log["_locked"] = getLockedText(context, false)
  log["_fullText"] = getFullText()
  log["_displayTime"] = getDisplayTime()
  log["_tag"] = getTagString()
  log["_formats"] = getFormats()
  return Gson().toJson(log)
}

fun Note.log(): String {
  val log = HashMap<String, Any>()
  log["note"] = this
  log["_title"] = getTitle()
  log["_text"] = getText()
  log["_image"] = getImageFile()
  log["_fullText"] = getFullText()
  log["_displayTime"] = getDisplayTime()
  log["_formats"] = getFormats()
  return Gson().toJson(log)
}

/**************************************************************************************
 ************* Content and Display Information Functions Functions ********************
 **************************************************************************************/
fun Note.getTitle(): String {
  val formats = getFormats()
  if (formats.isEmpty()) {
    return ""
  }
  val format = formats.first()
  return when {
    format.formatType === FormatType.HEADING -> format.text
    format.formatType === FormatType.SUB_HEADING -> format.text
    else -> ""
  }
}

fun Note.getText(): String {
  val formats = getFormats().toMutableList()
  if (formats.isEmpty()) {
    return ""
  }

  val format = formats.first()
  if (format.formatType == FormatType.HEADING || format.formatType == FormatType.SUB_HEADING) {
    formats.removeAt(0)
  }

  return formats
      .map { it.markdownText }
      .joinToString(separator = "\n")
      .trim()
}

fun Note.getImageFile(): String {
  val formats = getFormats()
  val format = formats.find { it.formatType === FormatType.IMAGE }
  return format?.text ?: ""
}

fun Note.getMarkdownTitle(context: Context, isMarkdownEnabled: Boolean): CharSequence {
  val titleString = getTitle()
  return when {
    titleString.isBlank() -> ""
    !isMarkdownEnabled -> renderMarkdown(context, removeMarkdownHeaders(titleString))
    else -> titleString
  }
}

fun Note.getMarkdownText(context: Context, isMarkdownEnabled: Boolean): CharSequence {
  return when {
    isMarkdownEnabled -> renderMarkdown(context, removeMarkdownHeaders(getText()))
    else -> getText()
  }
}

fun Note.getFullText(): String {
  val formats = getFormats()
  return formats.map { it -> it.markdownText }.joinToString(separator = "\n\n").trim()
}

fun Note.getUnreliablyStrippedText(context: Context): String {
  val builder = StringBuilder()
  builder.append(renderMarkdown(context, removeMarkdownHeaders(getTitle())))
  builder.append(renderMarkdown(context, removeMarkdownHeaders(getText())))
  return builder.toString().trim { it <= ' ' }
}

fun Note.getLockedText(context: Context, isMarkdownEnabled: Boolean): CharSequence {
  return when {
    this.locked -> "******************\n***********\n****************"
    else -> getMarkdownText(context, isMarkdownEnabled)
  }
}

fun Note.getDisplayTime(): String {
  val time = when {
    (this.updateTimestamp != 0L) -> this.updateTimestamp
    (this.timestamp != null) -> this.timestamp
    else -> 0
  }

  val format = when {
    Calendar.getInstance().timeInMillis - time < 1000 * 60 * 60 * 2 -> "hh:mm aa"
    else -> "dd MMMM"
  }
  return DateFormatter.getDate(format, time)
}

fun Note.getTagString(): String {
  val tags = getTags()
  return tags.map { it -> '`' + it.title + '`' }.joinToString(separator = " ")
}

fun Note.getTags(): Set<Tag> {
  val tags = HashSet<Tag>()
  for (tagID in getTagUUIDs()) {
    val tag = tagsDB.getByUUID(tagID)
    if (tag != null) {
      tags.add(tag)
    }
  }
  return tags
}

fun Note.toggleTag(tag: Tag) {
  val tags = getTagUUIDs()
  when (tags.contains(tag.uuid)) {
    true -> tags.remove(tag.uuid)
    false -> tags.add(tag.uuid)
  }
  this.tags = tags.joinToString(separator = ",")
}

fun Note.addTag(tag: Tag) {
  val tags = getTagUUIDs()
  when (tags.contains(tag.uuid)) {
    true -> return
    false -> tags.add(tag.uuid)
  }
  this.tags = tags.joinToString(separator = ",")
}

fun Note.removeTag(tag: Tag) {
  val tags = getTagUUIDs()
  when (tags.contains(tag.uuid)) {
    true -> tags.remove(tag.uuid)
    false -> return
  }
  this.tags = tags.joinToString(separator = ",")
}

/**************************************************************************************
 ******************************* Note Action Functions ********************************
 **************************************************************************************/

fun Note.mark(context: Context, noteState: NoteState) {
  this.state = noteState.name
  this.updateTimestamp = Calendar.getInstance().timeInMillis
  save(context)
}

fun Note.edit(context: Context) {
  if (this.locked) {
    if (context is ThemedActivity) {
      EnterPincodeBottomSheet.openUnlockSheet(context, object : EnterPincodeBottomSheet.PincodeSuccessListener {
        override fun onFailure() {
          edit(context)
        }

        override fun onSuccess() {
          openEdit(context)
        }
      })
    }
    return
  }
  openEdit(context)
}

fun Note.view(context: Context) {
  val intent = Intent(context, ViewAdvancedNoteActivity::class.java)
  intent.putExtra(INTENT_KEY_NOTE_ID, this.uid)
  context.startActivity(intent)
}

fun Note.viewDistractionFree(context: Context) {
  val intent = Intent(context, ViewAdvancedNoteActivity::class.java)
  intent.putExtra(INTENT_KEY_NOTE_ID, this.uid)
  intent.putExtra(INTENT_KEY_DISTRACTION_FREE, true)
  context.startActivity(intent)
}

fun Note.openEdit(context: Context) {
  val intent = Intent(context, CreateNoteActivity::class.java)
  intent.putExtra(INTENT_KEY_NOTE_ID, this.uid)
  context.startActivity(intent)
}


fun Note.share(context: Context) {
  CoreConfig.instance.noteActions(this).share(context)
}

fun Note.copy(context: Context) {
  CoreConfig.instance.noteActions(this).copy(context)
}

/**************************************************************************************
 ******************************* Database Functions ********************************
 **************************************************************************************/

fun Note.save(context: Context) {
  if (disableBackup) {
    saveWithoutSync(context)
    return
  }
  CoreConfig.instance.noteActions(this).save(context)
}

fun Note.saveWithoutSync(context: Context) {
  CoreConfig.instance.noteActions(this).offlineSave(context)
}

fun Note.saveToSync(context: Context) {
  CoreConfig.instance.noteActions(this).onlineSave(context)
}

fun Note.delete(context: Context) {
  if (disableBackup) {
    deleteWithoutSync(context)
    return
  }
  CoreConfig.instance.noteActions(this).delete(context)
}

fun Note.deleteWithoutSync(context: Context) {
  CoreConfig.instance.noteActions(this).offlineDelete(context)
}

fun Note.deleteToSync(context: Context) {
  CoreConfig.instance.noteActions(this).onlineDelete(context)
}

fun Note.softDelete(context: Context) {
  CoreConfig.instance.noteActions(this).softDelete(context)
}
