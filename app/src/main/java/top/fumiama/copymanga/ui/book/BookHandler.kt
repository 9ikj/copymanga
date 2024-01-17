package top.fumiama.copymanga.ui.book

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.request.RequestOptions
import com.google.gson.Gson
import kotlinx.android.synthetic.main.app_bar_main.*
import kotlinx.android.synthetic.main.card_book.*
import kotlinx.android.synthetic.main.fragment_book.*
import kotlinx.android.synthetic.main.line_2chapters.view.*
import kotlinx.android.synthetic.main.line_bookinfo.*
import kotlinx.android.synthetic.main.line_bookinfo_text.*
import kotlinx.android.synthetic.main.line_caption.view.*
import kotlinx.android.synthetic.main.line_chapter.view.*
import top.fumiama.copymanga.MainActivity.Companion.mainWeakReference
import top.fumiama.copymanga.json.BookInfoStructure
import top.fumiama.copymanga.json.ChapterStructure
import top.fumiama.copymanga.json.ThemeStructure
import top.fumiama.copymanga.json.VolumeStructure
import top.fumiama.copymanga.manga.Reader
import top.fumiama.copymanga.template.http.AutoDownloadHandler
import top.fumiama.copymanga.template.http.AutoDownloadThread
import top.fumiama.copymanga.tools.api.CMApi
import top.fumiama.copymanga.tools.ui.GlideBlurTransformation
import top.fumiama.copymanga.tools.ui.GlideHideLottieViewListener
import top.fumiama.copymanga.tools.ui.Navigate
import top.fumiama.copymanga.ui.comicdl.ComicDlFragment
import top.fumiama.copymanga.ui.comicdl.ComicDlFragment.Companion.json
import top.fumiama.copymanga.ui.vm.ViewMangaActivity
import top.fumiama.dmzj.copymanga.R
import java.io.File
import java.lang.Thread.sleep
import java.lang.ref.WeakReference

class BookHandler(private val th: WeakReference<BookFragment>, val path: String)
    : AutoDownloadHandler(th.get()?.getString(R.string.bookInfoApiUrl)?.format(CMApi.myHostApiUrl, path)?: "",
    BookInfoStructure::class.java,
    Looper.myLooper()!!){
    private val that get() = th.get()
    private var hasToastedError = false
    get(){
        val re = field
        field = true
        return re
    }
    var book: BookInfoStructure? = null
    private var complete = false
    var ads = emptyArray<AutoDownloadThread>()
    var gpws = arrayOf<String>()
    var keys = arrayOf<String>()
    var cnts = intArrayOf()
    var vols: Array<VolumeStructure>? = null
    var chapterNames = arrayOf<String>()
    var collect: Int = -1
    private val divider get() = that?.layoutInflater?.inflate(R.layout.div_h, that?.fbl, false)

    var urlArray = arrayOf<String>()

    override fun handleMessage(msg: Message) {
        super.handleMessage(msg)
        when(msg.what){
            //0 -> setLayouts()
            1 -> setCover()
            2 -> setTexts()
            3 -> setAuthorsAndTags()
            4 -> setOverScale()
            6 -> if(complete) that?.navigate2dl()
            7 -> setVolumes()
            8 -> that?.apply { fbl?.addView(msg.obj as View) }
            9 -> endSetLayouts()
        }
    }

    override fun onError() {
        super.onError()
        if(exit) return
        if(!hasToastedError) that?.activity?.runOnUiThread {
            Toast.makeText(that?.context, R.string.null_book, Toast.LENGTH_SHORT).show()
            that?.apply { findNavController().popBackStack() }
        }
    }

    override fun setGsonItem(gsonObj: Any): Boolean {
        val pass = super.setGsonItem(gsonObj)
        book = gsonObj as BookInfoStructure
        return pass
    }

    override fun getGsonItem() = book
    override fun doWhenFinishDownload() {
        super.doWhenFinishDownload()
        if(exit) return
        if(keys.isEmpty()) book?.results?.groups?.values?.forEach{
            keys += it.name
            gpws += it.path_word
            if (it.count == 0) {
                it.count = 1
            }
            cnts += it.count
            Log.d("MyBFH", "Add caption: ${it.name} @ ${it.path_word} of ${it.count}")
        }
        for (i in 1..4) {
            sendEmptyMessageDelayed(i, (100*i).toLong())
        }
        if(vols?.isEmpty() != false) initComicData()
    }

    private fun endSetLayouts() {
        if (exit) return
        that?.fbloading?.visibility = View.GONE
        complete = true
        that?.setStartRead()
        that?.setAddToShelf()
        Log.d("MyBH", "Set complete: true")
    }
    private fun setOverScale(){
        that?.fbov?.setScaleView(that!!.lbibg)
    }

    private fun setCover() {
        that?.apply {
            that?.layoutInflater?.inflate(R.layout.line_bookinfo, that?.fbl, false)?.let {
                fbl.addView(it)
                setInfoHeight(it)
            }
            book?.results?.comic?.cover?.let { cover ->
                val load = Glide.with(this).load(
                    GlideUrl(CMApi.proxy?.wrap(cover)?:cover, CMApi.myGlideHeaders)
                ).timeout(10000).addListener(GlideHideLottieViewListener(WeakReference(laic)))
                load.into(imic)
                context?.let { it1 -> GlideBlurTransformation(it1) }
                    ?.let { it2 -> RequestOptions.bitmapTransform(it2) }
                    ?.let { it3 -> load.apply(it3).into(lbibg) }
            }
            imf?.visibility = View.GONE
            fbl?.addView(divider)
        }
    }

    private fun getThemeSeq(authors: Array<ThemeStructure>): CharSequence{
        var re = ""
        for(author in authors) re += author.name + ' '
        return re
    }

    private fun setTexts(){
        //that?.tic?.text = book?.name
        that?.tic?.visibility = View.GONE
        mainWeakReference?.get()?.toolbar?.title = book?.results?.comic?.name
        that?.btauth?.text = book?.results?.comic?.author?.let { getThemeSeq(it) }
        that?.bttag?.text = book?.results?.comic?.theme?.let { getThemeSeq(it) }
        that?.bthit?.text = that?.getString(R.string.text_format_hit)?.let { String.format(
            it,
            book?.results?.comic?.popular
        ) }?:""
        that?.btsub?.text = that?.getString(R.string.text_format_stat)?.let { String.format(
            it,
            book?.results?.comic?.status?.display
        ) }?:""
        that?.bttime?.text = book?.results?.comic?.datetime_updated
        val v = that?.layoutInflater?.inflate(R.layout.line_text_info, that?.fbl, false)
        (v as TextView).text = book?.results?.comic?.brief
        that?.fbl?.addView(v)
        that?.fbl?.addView(divider)
    }

    private fun setInfoHeight(v: View){
        v.viewTreeObserver.addOnGlobalLayoutListener {
            Log.d("MyMy", "Width: ${v.width}")
            val newH = (v.width * 4.0 / 9.0 + 0.5).toInt()
            v.layoutParams.height = newH
            v.invalidate()
        }
    }

    private fun setTheme(caption: String, themeStructure: Array<ThemeStructure>, nav: Int) {
        that?.apply {
            val t = layoutInflater.inflate(R.layout.line_caption, fbl, false)
            t.tcptn.text = caption
            fbl.addView(t)
            fbl.addView(layoutInflater.inflate(R.layout.div_h, fbl, false))
        }
        var line: View? = null
        val last = themeStructure.size - 1
        themeStructure.onEachIndexed { i, it ->
            if(line == null) {
                if(i == last) {
                    line = that?.layoutInflater?.inflate(R.layout.line_chapter, that!!.fbl, false)
                    line?.lcc?.apply {
                        lct.text = it.name
                        setOnClickListener { _ ->
                            loadVolume(it.name, it.path_word, nav)
                        }
                    }
                    that?.fbl?.addView(line)
                } else {
                    line = that?.layoutInflater?.inflate(R.layout.line_2chapters, that!!.fbl, false)
                    line?.l2cl?.apply {
                        lct.text = it.name
                        setOnClickListener { _ ->
                            loadVolume(it.name, it.path_word, nav)
                        }
                    }
                }
            } else line?.l2cr?.apply {
                lct.text = it.name
                setOnClickListener { _ ->
                    loadVolume(it.name, it.path_word, nav)
                }
                that?.fbl?.addView(line)
                line = null
            }
        }
    }

    private fun setAuthorsAndTags() {
        that?.apply {
            book?.results?.comic?.apply {
                author?.let {
                    setTheme(
                        getString(R.string.author),
                        it,
                        R.id.action_nav_book_to_nav_author
                    )
                }
                fbl.addView(layoutInflater.inflate(R.layout.div_h, fbl, false))
                theme?.let {
                    setTheme(
                        getString(R.string.caption),
                        it,
                        R.id.action_nav_book_to_nav_caption
                    )
                }
            }
        }
    }

    private fun addVolumesView(v: View) {
        obtainMessage(8, v).sendToTarget()
    }

    private fun setVolumes() = Thread {
        if (exit) return@Thread
        that?.apply {
            book?.results?.apply {
                ViewMangaActivity.fileArray = arrayOf()
                urlArray = arrayOf()
                ViewMangaActivity.uuidArray = arrayOf()
                var i = 0
                var last = -1
                vols?.forEachIndexed { groupIndex, v ->
                    if(exit) return@Thread
                    addVolumesView(layoutInflater.inflate(R.layout.div_h, fbl, false))
                    val t = layoutInflater.inflate(R.layout.line_caption, fbl, false)
                    t.tcptn.text = keys[groupIndex]
                    addVolumesView(t)
                    addVolumesView(layoutInflater.inflate(R.layout.div_h, fbl, false))
                    var line: View? = null
                    last += v.results.list.size
                    v.results.list.forEach {
                        urlArray += CMApi.getChapterInfoApiUrl(
                            comic.path_word,
                            it.uuid
                        )?:""
                        ViewMangaActivity.fileArray += CMApi.getZipFile(context?.getExternalFilesDir(""), comic.name, keys[groupIndex], it.name)
                        chapterNames += it.name
                        ViewMangaActivity.uuidArray += it.uuid
                        Log.d("MyBH", "i = $i, last=$last, add chapter ${it.name}, line is null: ${line == null}")
                        that?.isOnPause?.let { isOnPause ->
                            while (isOnPause && !exit) sleep(1000)
                            if (exit) return@Thread
                        }?:return@Thread
                        if(line == null) {
                            if(i == last) {
                                line = layoutInflater.inflate(R.layout.line_chapter, that!!.fbl, false)
                                line?.lcc?.apply {
                                    lct.text = it.name
                                    Log.d("MyBH", "add last single chapter ${it.name}")
                                    val index = i
                                    setOnClickListener { Reader.viewMangaAt(comic.name, index, urlArray) }
                                }
                                line?.let { l -> addVolumesView(l) }

                            } else {
                                line = layoutInflater.inflate(R.layout.line_2chapters, that!!.fbl, false)
                                line?.l2cl?.apply {
                                    lct.text = it.name
                                    val index = i
                                    setOnClickListener { Reader.viewMangaAt(comic.name, index, urlArray) }
                                }
                            }
                        } else line?.l2cr?.apply {
                            lct.text = it.name
                            val index = i
                            setOnClickListener { Reader.viewMangaAt(comic.name, index, urlArray) }
                            line?.let { l -> addVolumesView(l) }
                            line = null
                        }
                        i++
                    }
                }
                sendEmptyMessage(9) // end set layout
            }
        }
    }.start()

    private fun loadVolume(name: String, path: String, nav: Int){
        if(complete) {
            Log.d("MyBH", "start to load chapter")
            val bundle = Bundle()
            bundle.putString("name", name)
            bundle.putString("path", path)
            that?.apply { Navigate.safeNavigateTo(findNavController(), nav, bundle) }
        }
    }

    private fun initComicData() = Thread {
        var volumes = emptyArray<VolumeStructure>()
        val counts = cnts.clone()
        gpws.forEachIndexed { i, gpw ->
            Log.d("MyBFH", "下载:$gpw")
            var offset = 0
            val times = counts[i] / 100
            val remain = counts[i] % 100
            val re = arrayOfNulls<VolumeStructure>(if(remain != 0) (times+1) else (times))
            if (re.isEmpty()) that?.activity?.runOnUiThread {
                Toast.makeText(that?.context, "获取${gpw}失败", Toast.LENGTH_SHORT).show()
                return@runOnUiThread
            }
            Log.d("MyBFH", "${i}卷共${if(times == 0) 1 else times}次加载")
            do {
                counts[i] = counts[i] - 100
                CMApi.getGroupInfoApiUrl(path, gpw, offset)?.let {
                    Log.d("MyBFH", "get api: $it")
                    if(ComicDlFragment.exit) return@Thread
                    val ad = AutoDownloadThread(it) { result ->
                        val r = Gson().fromJson(result?.decodeToString(), VolumeStructure::class.java)
                        re[r.results.offset / 100] = r
                        Log.d("MyBFH", "第${i}卷返回, 大小: ${r.results.list.size}")
                    }
                    ads += ad
                    ad.start()
                    offset += 100
                    sleep(1000)
                }
            } while (counts[i] > 0)
                var c = 0
                while (c++ < 80) {
                    sleep(1000)
                    if(ComicDlFragment.exit) return@Thread
                    if(re.all { it != null }) break
                }
                if(re.isNotEmpty()) {
                    val r = re[0]
                    var s = emptyArray<ChapterStructure>()
                    re.forEach {
                        it?.results?.list?.forEach {
                            s += it
                        }
                    }
                    r?.results?.list = s
                    r?.apply { volumes += this }
                } else re[0]?.apply { volumes += this }
        }
        var c = 0
        while (c < 80 && volumes.size != gpws.size) {
            sleep(1000)
            if(ComicDlFragment.exit) return@Thread
            Log.d("MyBFH", "已有：${volumes.size} 共：${gpws.size}")
            c++
        }
        if (volumes.size == gpws.size) {
            that?.activity?.runOnUiThread {
                saveVolumes(volumes)
                sendEmptyMessage(7)
            }
        }
    }.start()

    private fun saveVolumes(volumes: Array<VolumeStructure>) {
        that?.context?.getExternalFilesDir("")?.let { home ->
            book?.results?.comic?.name?.let { name ->
                val mangaFolder = File(home, name)
                if(!mangaFolder.exists()) mangaFolder.mkdirs()
                json = Gson().toJson(volumes)
                File(mangaFolder, "info.json").writeText(json!!)
                File(mangaFolder, "grps.json").writeText(Gson().toJson(keys))
                that?.apply {
                    Thread {
                        var cnt = 0
                        var success = false
                        while (cnt++ < 10 && !success) {
                            sleep(1000)
                            if (exit) return@Thread
                            File(mangaFolder, "head.jpg").let { head ->
                                val fo = head.outputStream()
                                try {
                                    imic.drawable.toBitmap().compress(Bitmap.CompressFormat.JPEG, 90, fo)
                                    success = true
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                fo.close()
                            }
                        }
                        if (!success) that?.activity?.runOnUiThread {
                            Toast.makeText(that?.context, R.string.download_cover_timeout, Toast.LENGTH_SHORT).show()
                        }
                    }.start()
                }
            }
        }
        vols = volumes
    }
}
