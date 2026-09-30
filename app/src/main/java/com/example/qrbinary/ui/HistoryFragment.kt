package com.example.qrbinary.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import com.example.qrbinary.R
import com.example.qrbinary.data.HistoryStore

class HistoryFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_history, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        val list = view.findViewById<RecyclerView>(R.id.history_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = HistoryAdapter(HistoryStore(requireContext()).all())
    }
}

private class HistoryAdapter(
    private val items: List<com.example.qrbinary.data.HistoryEntry>
) : RecyclerView.Adapter<HistoryAdapter.Holder>() {
    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.title)
        val details: TextView = v.findViewById(R.id.details)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val x = items[position]
        holder.title.text = "${x.format} — ${x.bytes} байт"
        holder.details.text = "${x.fileName}\nSHA-256: ${x.sha256}"
    }

    override fun getItemCount() = items.size
}
