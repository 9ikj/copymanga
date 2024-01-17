package top.fumiama.copymanga.ui.home

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.google.gson.Gson
import com.lapism.search.internal.SearchLayout
import kotlinx.android.synthetic.main.card_book_plain.view.*
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.line_word.view.*
import kotlinx.android.synthetic.main.viewpage_horizonal.view.*
import top.fumiama.copymanga.MainActivity
import top.fumiama.copymanga.MainActivity.Companion.ime
import top.fumiama.copymanga.MainActivity.Companion.mainWeakReference
import top.fumiama.copymanga.json.BookListStructure
import top.fumiama.copymanga.template.general.NoBackRefreshFragment
import top.fumiama.copymanga.template.http.AutoDownloadThread
import top.fumiama.copymanga.tools.api.CMApi
import top.fumiama.copymanga.tools.ui.GlideHideLottieViewListener
import top.fumiama.copymanga.tools.ui.Navigate
import top.fumiama.dmzj.copymanga.R
import java.lang.Thread.sleep
import java.lang.ref.WeakReference

class HomeFragment : NoBackRefreshFragment(R.layout.fragment_home) {
    lateinit var homeHandler: HomeHandler

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if(isFirstInflate) {
            val tb = mainWeakReference?.get()?.toolsBox
            val netInfo = tb?.netInfo
            if(netInfo != null && netInfo != tb.transportStringNull && netInfo != tb.transportStringError) Thread {
                val l = MainActivity.member?.refreshAvatar()
                if (l?.code != 200) {
                    mainWeakReference?.get()?.runOnUiThread {
                        Toast.makeText(context, "${getString(R.string.login_get_avatar_failed)}: 代码${l?.code}, 信息: ${l?.message}", Toast.LENGTH_SHORT).show()
                    }
                    MainActivity.member?.logout()
                }
            }.start()
            homeHandler = HomeHandler(WeakReference(this))

            val theme = resources.newTheme()
            swiperefresh?.setColorSchemeColors(
                resources.getColor(R.color.colorAccent, theme),
                resources.getColor(R.color.colorBlue2, theme),
                resources.getColor(R.color.colorGreen, theme))
            swiperefresh?.isEnabled = true

            fhl?.setPadding(0, 0, 0, navBarHeight)

            fhs?.apply {
                isNestedScrollingEnabled = true
                val recyclerView = findViewById<RecyclerView>(R.id.search_recycler_view)
                recyclerView.isNestedScrollingEnabled = true
                recyclerView.setPadding(0, 0, 0, navBarHeight)
                setAdapterLayoutManager(LinearLayoutManager(context))
                val adapter = ListViewHolder(recyclerView).RecyclerViewAdapter()
                setAdapter(adapter)
                navigationIconSupport = SearchLayout.NavigationIconSupport.SEARCH
                setMicIconImageResource(R.drawable.ic_setting_search)
                val micView = findViewById<ImageButton>(R.id.search_image_view_mic)
                setClearFocusOnBackPressed(true)
                setOnNavigationClickListener(object : SearchLayout.OnNavigationClickListener {
                    override fun onNavigationClick(hasFocus: Boolean) {
                        if (hasFocus()) {
                            clearFocus()
                        }
                        else requestFocus()
                    }
                })
                setTextHint(android.R.string.search_go)

                setOnQueryTextListener(object : SearchLayout.OnQueryTextListener {
                    var lastChangeTime = 0L
                    var lastSearch: String = ""
                    override fun onQueryTextChange(newText: CharSequence): Boolean {
                        if (newText.contentEquals("__notice_focus_change__") || newText.contentEquals(lastSearch)) return true
                        postDelayed({
                            val diff = System.currentTimeMillis() - lastChangeTime
                            if(diff > 500) {
                                if (newText.isNotEmpty()) {
                                    Log.d("MyHF", "new text: $newText")
                                    lastSearch = newText.toString()
                                    adapter.refresh(newText)
                                }
                            }
                        }, 1024)
                        lastChangeTime = System.currentTimeMillis()
                        return true
                    }

                    override fun onQueryTextSubmit(query: CharSequence): Boolean {
                        /*if(query.isNotEmpty()) {
                            val key = query.toString()
                            Toast.makeText(context, key, Toast.LENGTH_SHORT).show()
                        }*/
                        Log.d("MyHF", "recover text: $lastSearch")
                        setTextQuery(lastSearch, false)
                        return true
                    }
                })
                setOnMicClickListener(object : SearchLayout.OnMicClickListener {
                    val types = arrayOf("", "name", "author", "local")
                    var i = 0
                    override fun onMicClick() {
                        val typeNames = resources.getStringArray(R.array.search_types)
                        AlertDialog.Builder(context)
                            .setTitle(R.string.set_search_types)
                            .setIcon(R.mipmap.ic_launcher)
                            .setSingleChoiceItems(ArrayAdapter(context, R.layout.line_choice_list, typeNames), i){ d, p ->
                                adapter.type = types[p]
                                i = p
                                d.cancel()
                            }.show()
                    }
                })

                setOnFocusChangeListener(object : SearchLayout.OnFocusChangeListener {
                    override fun onFocusChange(hasFocus: Boolean) {
                        Log.d("MyHF", "fhs onFocusChange: $hasFocus")
                        navigationIconSupport = if (hasFocus) {
                            setTextQuery("__notice_focus_change__", true)
                            SearchLayout.NavigationIconSupport.ARROW
                        }
                        else {
                            micView.postDelayed({ micView?.visibility = View.VISIBLE }, 233)
                            SearchLayout.NavigationIconSupport.SEARCH
                        }
                    }
                })

                setOnTouchListener { _, e ->
                    Log.d("MyHF", "fhns on touch")
                    if (e.action == MotionEvent.ACTION_UP && mSearchEditText?.text?.isNotEmpty() == true) {
                        ime?.hideSoftInputFromWindow(mainWeakReference?.get()?.window?.decorView?.windowToken, 0)
                    }
                    false
                }
            }

            Thread{
                homeHandler.obtainMessage(-1, true).sendToTarget()
                while(mainWeakReference?.get()?.isDrawerClosed != true) sleep(233)
                //homeHandler.sendEmptyMessage(6)    //removeAllViews
                homeHandler.fhib = null
                sleep(600)
                homeHandler.startLoad()
            }.start()
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        homeHandler.destroy()
    }

    inner class ViewData(itemView: View) : RecyclerView.ViewHolder(itemView) {
        inner class RecyclerViewAdapter :
            RecyclerView.Adapter<ViewData>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewData {
                return ViewData(layoutInflater.inflate(R.layout.viewpage_horizonal, parent, false))
            }

            override fun onBindViewHolder(holder: ViewData, position: Int) {
                val thisBanner = homeHandler.index?.results?.banners?.get(position)
                thisBanner?.cover?.let {
                    //Log.d("MyHomeFVP", "Load img: $it")
                    Glide.with(this@HomeFragment).load(
                        GlideUrl(CMApi.proxy?.wrap(it)?:it, CMApi.myGlideHeaders)
                    ).addListener(GlideHideLottieViewListener(WeakReference(holder.itemView.lai))).timeout(10000).into(holder.itemView.vpi)
                }
                holder.itemView.vpt.text = thisBanner?.brief
                holder.itemView.vpc.setOnClickListener {
                    val bundle = Bundle()
                    homeHandler.index?.results?.banners?.get(position)?.comic?.path_word?.let { it1 -> bundle.putString("path", it1) }
                    Navigate.safeNavigateTo(findNavController(), R.id.action_nav_home_to_nav_book, bundle)
                }
            }

            override fun getItemCount(): Int = homeHandler.index?.results?.banners?.size?:0
        }
    }

    inner class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        inner class RecyclerViewAdapter :
            RecyclerView.Adapter<ListViewHolder>() {
            private var results: BookListStructure? = null
            var type = ""
            private var query: String? = null
            private var count = 0
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
                return ListViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.line_word, parent, false)
                )
            }

            @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
            override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
                Log.d("MyMain", "Bind open at $position")
                if (position == itemCount-1) {
                    holder.itemView.tn.setText(R.string.button_more)
                    holder.itemView.ta.text = "搜索 \"$query\""
                    holder.itemView.tb.text = "共 $count 条结果"
                    holder.itemView.lwi.visibility = View.INVISIBLE
                    holder.itemView.lwc.setOnClickListener {
                        if (query?.isNotEmpty() != true) return@setOnClickListener
                        val bundle = Bundle()
                        bundle.putCharSequence("query", query)
                        bundle.putString("type", type)
                        Navigate.safeNavigateTo(findNavController(), R.id.action_nav_home_to_nav_search, bundle)
                    }
                    return
                }
                results?.results?.list?.get(position)?.apply {
                    holder.itemView.lwi.visibility = View.VISIBLE
                    holder.itemView.tn.text = name
                    holder.itemView.ta.text = author.let {
                        var t = ""
                        it.forEach { ts ->
                            t += ts.name + " "
                        }
                        return@let t
                    }
                    holder.itemView.tb.text = popular.toString()
                    context?.let {
                        Glide.with(it)
                            .load(GlideUrl(CMApi.proxy?.wrap(cover)?:cover, CMApi.myGlideHeaders))
                            .addListener(GlideHideLottieViewListener(WeakReference(holder.itemView.laic)))
                            .into(holder.itemView.imic)
                    }
                    holder.itemView.lwc.setOnClickListener {
                        val bundle = Bundle()
                        bundle.putString("path", path_word)
                        Navigate.safeNavigateTo(findNavController(), R.id.action_nav_home_to_nav_book, bundle)
                    }
                    holder.itemView.lwc.layoutParams.height = fhs.width / 4
                }
            }

            override fun getItemCount() = (results?.results?.list?.size?:0) + if (query?.isNotEmpty() == true) 1 else 0

            fun refresh(q: CharSequence) {
                query = q.toString()
                activity?.apply {
                    AutoDownloadThread(getString(R.string.searchApiUrl).format(CMApi.myHostApiUrl, 0, query, type)) {
                        results = Gson().fromJson(it?.decodeToString(), BookListStructure::class.java)
                        count = results?.results?.total?:0
                        runOnUiThread { notifyDataSetChanged() }
                    }.start()
                }
            }
        }
    }
}