package com.example.hideandseek

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class MessageAdapter(context: Context, messages: List<ChatClass>) :
    ArrayAdapter<ChatClass>(context, 0, messages) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, parent, false)
        }
        val message = getItem(position)
        val textView = convertView!!.findViewById<TextView>(android.R.id.text1)
        textView.text = "${message?.userName} : ${message?.message}"
        return convertView
    }
}