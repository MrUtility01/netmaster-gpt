package com.example.netmaster.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.netmaster.ai.*
import com.example.netmaster.data.*
import com.example.netmaster.domain.*
import com.example.netmaster.export.ExportManager
import com.example.netmaster.search.GlobalSearchEngine
import com.example.netmaster.search.RagEngine
import com.example.netmaster.search.SearchEngine
import com.example.netmaster.search.UnifiedCatalogSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.MessageDigest
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64

data class AppLoadState(
    val ready: Boolean = false,
    val stage: String = "در حال آماده‌سازی برنامه…",
    val progress: Int = 0,
    val indexing: Boolean = false,
    val indexProgress: Int = 0,
    val indexSeconds: Int = 0,
    val error: String? = null
)

data class PcapLoadState(val busy:Boolean=false,val seconds:Int=0,val error:String?=null)

class NetMasterViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = app.applicationContext
    private val dao = AppDatabase.get(app).dao()
    private val authPrefs = appContext.getSharedPreferences("netmaster_auth", Context.MODE_PRIVATE)
    private val repo = ContentRepository(app)
    private val offlineAi: AiProvider = OfflineAiProvider()
    private var localAi: AiProvider = LocalLlmProvider("http://10.0.2.2:11434", "llama3.2")
    private var activeAi: AiProvider = offlineAi
    val searchEngine = SearchEngine()
    val globalSearchEngine = GlobalSearchEngine(searchEngine)
    val unifiedCatalogSearch = UnifiedCatalogSearch()
    val ragEngine = RagEngine()
    val configDiffEngine = ConfigDiffEngine()
    val packetEngine = PacketAnalysisEngine()
    val pcapParser = PcapParser()
    val packetFilterEngine = PacketFilterEngine()
    val reviewEngine = ReviewEngine()
    val commandSimulator = CommandSimulator()
    val twinEngine = DigitalTwinEngine(commandSimulator)
    private var scenarioEngine = ScenarioEngine(emptyList())
    private var coachEngine = TroubleshootingCoach(commandSimulator, scenarioEngine)
    val engineeringEngine = FieldEngineeringEngine()

    private val _curriculum = MutableStateFlow<Curriculum?>(null); val curriculum = _curriculum.asStateFlow()
    private val _progress = MutableStateFlow<Map<String, ProgressEntity>>(emptyMap()); val progress = _progress.asStateFlow()
    private val _notes = MutableStateFlow<List<NoteEntity>>(emptyList()); val notes = _notes.asStateFlow()
    private val _bookmarks = MutableStateFlow<Set<String>>(emptySet()); val bookmarks = _bookmarks.asStateFlow()
    private val _aiResponse = MutableStateFlow<AiResponse?>(null); val aiResponse = _aiResponse.asStateFlow()
    private val _twin = MutableStateFlow(twinEngine.defaultState()); val twin = _twin.asStateFlow()
    private val _exported = MutableStateFlow<File?>(null); val exported = _exported.asStateFlow()
    private val _incidents = MutableStateFlow<List<IncidentEntity>>(emptyList()); val incidents = _incidents.asStateFlow()
    private val _knowledge = MutableStateFlow(KnowledgeGraphView(emptyList(), emptyList())); val knowledge = _knowledge.asStateFlow()
    private val _captures = MutableStateFlow<List<PacketCaptureEntity>>(emptyList()); val captures = _captures.asStateFlow()
    private val _configs = MutableStateFlow<List<ConfigSnapshotEntity>>(emptyList()); val configs = _configs.asStateFlow()
    private val _dueReviews = MutableStateFlow<List<ReviewItemEntity>>(emptyList()); val dueReviews = _dueReviews.asStateFlow()
    private val _selectedIncident = MutableStateFlow<IncidentWorkspace?>(null); val selectedIncident = _selectedIncident.asStateFlow()
    private val _filteredPackets = MutableStateFlow<List<PacketRecord>>(emptyList()); val filteredPackets = _filteredPackets.asStateFlow()
    private val _basePackets = MutableStateFlow<List<PacketRecord>>(emptyList())
    private val _filterError = MutableStateFlow<String?>(null); val filterError = _filterError.asStateFlow()
    private val _simOutput = MutableStateFlow(""); val simOutput = _simOutput.asStateFlow()
    private val _coach = MutableStateFlow<CoachSession?>(null); val coach = _coach.asStateFlow()
    private val _vectorCount = MutableStateFlow(0); val vectorCount = _vectorCount.asStateFlow()
    private val _deepLessonCount = MutableStateFlow(0); val deepLessonCount = _deepLessonCount.asStateFlow()
    private val _scenarioCount = MutableStateFlow(0); val scenarioCount = _scenarioCount.asStateFlow()
    private val _aiBackend = MutableStateFlow("OFFLINE"); val aiBackend = _aiBackend.asStateFlow()
    private val _searchResults = MutableStateFlow<List<com.example.netmaster.search.GlobalSearchHit>>(emptyList()); val searchResults = _searchResults.asStateFlow()
    private val _selectedConfig = MutableStateFlow<ConfigSnapshotEntity?>(null); val selectedConfig = _selectedConfig.asStateFlow()
    private val _masterCurriculum = MutableStateFlow<MasterCurriculum?>(null); val masterCurriculum = _masterCurriculum.asStateFlow()
    private val _labCatalog = MutableStateFlow<LabCatalog?>(null); val labCatalog = _labCatalog.asStateFlow()
    private val _aiPlaybooks = MutableStateFlow<AiPlaybookCatalog?>(null); val aiPlaybooks = _aiPlaybooks.asStateFlow()
    private val _catalogCount = MutableStateFlow(0); val catalogCount = _catalogCount.asStateFlow()
    private val _engineeringInvestigation = MutableStateFlow<FieldEngineeringEngine.Investigation?>(null); val engineeringInvestigation = _engineeringInvestigation.asStateFlow()
    private val _engineeringPassport = MutableStateFlow<FieldEngineeringEngine.Passport?>(null); val engineeringPassport = _engineeringPassport.asStateFlow()
    private val _engineeringFaults = MutableStateFlow(engineeringEngine.faultCatalog()); val engineeringFaults = _engineeringFaults.asStateFlow()
    private val _engineeringSelectedFault = MutableStateFlow<FieldEngineeringEngine.FaultCase?>(null); val engineeringSelectedFault = _engineeringSelectedFault.asStateFlow()
    private val _twinVerification = MutableStateFlow<TwinVerification?>(null); val twinVerification = _twinVerification.asStateFlow()
    private val _loadState = MutableStateFlow(AppLoadState()); val loadState = _loadState.asStateFlow()
    private val _pcapLoad = MutableStateFlow(PcapLoadState()); val pcapLoad = _pcapLoad.asStateFlow()
    private val _configDiff = MutableStateFlow<ConfigDiffSummary?>(null); val configDiff = _configDiff.asStateFlow()
    private val _packetAnalysis = MutableStateFlow<PacketAnalysisReport?>(null); val packetAnalysis = _packetAnalysis.asStateFlow()
    private val _authenticated = MutableStateFlow(authPrefs.getBoolean(KEY_SESSION, false)); val authenticated = _authenticated.asStateFlow()
    private val _aiBusy = MutableStateFlow(false); val aiBusy = _aiBusy.asStateFlow()
    private var indexingJob: Job? = null

    init { viewModelScope.launch { loadContentSafely(); refresh() } }

    private suspend fun loadContentSafely() {
        try {
            loadContent()
        } catch (t: Throwable) {
            _loadState.value = _loadState.value.copy(ready = false, indexing = false, error = t.message?.take(240) ?: "خطای نامشخص در بارگذاری محتوا")
        }
    }

    fun retryLoad() {
        viewModelScope.launch {
            _loadState.value = AppLoadState()
            loadContentSafely()
            refresh()
        }
    }

    private suspend fun loadContent() {
        fun stage(p: Int, text: String) { _loadState.value = _loadState.value.copy(progress = p, stage = text) }
        stage(5, "در حال خواندن برنامه آموزشی…")
        val content = withContext(Dispatchers.IO) {
            QuadContent(
                base = repo.loadCurriculum(),
                scenarios = repo.loadScenarios(),
                master = repo.loadMasterCurriculum(),
                labs = repo.loadLabs(),
                ai = repo.loadAiPlaybooks(),
                references = repo.loadReferences()
            )
        }
        val base = content.base
        val scenarios = content.scenarios
        val master = content.master
        val labs = content.labs
        val ai = content.ai
        val references = content.references
        stage(28, "محتوا خوانده شد؛ در حال ساخت کاتالوگ…")
        val deep = DeepContentPack()
        _curriculum.value = base
        _deepLessonCount.value = 0
        _scenarioCount.value = scenarios.metadata.scenarioCount
        _masterCurriculum.value = master
        _labCatalog.value = labs
        _aiPlaybooks.value = ai
        withContext(Dispatchers.Default) {
            unifiedCatalogSearch.rebuild(base, deep, scenarios, master, labs, ai, references)
        }
        _catalogCount.value = unifiedCatalogSearch.size()
        scenarioEngine = ScenarioEngine(scenarios.scenarios)
        coachEngine = TroubleshootingCoach(commandSimulator, scenarioEngine)
        stage(50, "برنامه آماده شد؛ ایندکس جستجو در پس‌زمینه ساخته می‌شود…")
        _loadState.value = _loadState.value.copy(ready = true, progress = 100, stage = "آماده است؛ در حال تکمیل جستجوی هوشمند…", indexing = true, indexProgress = 0, indexSeconds = 0)

        val scenarioLessons = DeepContentMapper.scenariosAsLessons(scenarios)
        val all = base.levels.flatMap { it.lessons } + scenarioLessons
        val referenceChunks = referenceRagChunks(references)
        indexingJob?.cancel()
        indexingJob = viewModelScope.launch(Dispatchers.Default) {
            val started = System.currentTimeMillis()
            val storedVersion = authPrefs.getString(KEY_RAG_CONTENT_VERSION, null)
            val persisted = if (storedVersion == RAG_CONTENT_VERSION) {
                withContext(Dispatchers.IO) { dao.ragVectors() }
            } else {
                emptyList()
            }
            val expectedContentIds = all.map { "lesson:${it.id}" }.toSet() + referenceChunks.map { "ref:${it.source.removePrefix("reference/")}" }.toSet()
            val persistedContentIds = persisted.mapNotNull { contentKey(it.source) }.toSet()
            if (storedVersion == RAG_CONTENT_VERSION && persisted.isNotEmpty() && persistedContentIds == expectedContentIds) {
                ragEngine.hydrate(persisted)
                _vectorCount.value = persisted.size
                authPrefs.edit().putString(KEY_RAG_CONTENT_VERSION, RAG_CONTENT_VERSION).apply()
                withContext(Dispatchers.Main.immediate) {
                    _loadState.value = _loadState.value.copy(indexing = false, indexProgress = 100, indexSeconds = ((System.currentTimeMillis() - started) / 1000).toInt(), stage = "جستجوی هوشمند آماده است.")
                }
            } else {
                buildVectors(all, referenceChunks, true) { progress ->
                    val elapsed = ((System.currentTimeMillis() - started) / 1000).toInt()
                    _loadState.value = _loadState.value.copy(indexing = true, indexProgress = progress, indexSeconds = elapsed, stage = "ساخت ایندکس جستجو… $progress٪")
                }
                authPrefs.edit().putString(KEY_RAG_CONTENT_VERSION, RAG_CONTENT_VERSION).apply()
                withContext(Dispatchers.Main.immediate) {
                    _loadState.value = _loadState.value.copy(indexing = false, indexProgress = 100, indexSeconds = ((System.currentTimeMillis() - started) / 1000).toInt(), stage = "جستجوی هوشمند آماده است.")
                }
            }
        }
    }

    private data class QuadContent(
        val base: Curriculum,
        val scenarios: ScenarioPack,
        val master: MasterCurriculum,
        val labs: LabCatalog,
        val ai: AiPlaybookCatalog,
        val references: ReferenceBundle
    )

    private fun contentKey(source: String): String? {
        when {
            source.startsWith("curriculum/") -> source.removePrefix("curriculum/").substringBefore('/').takeIf { it.isNotBlank() }?.let { "lesson:$it" }
            source.startsWith("reference/") -> source.removePrefix("reference/").takeIf { it.isNotBlank() }?.let { "ref:$it" }
            else -> null
        }
    }

    private fun referenceRagChunks(bundle: ReferenceBundle): List<com.example.netmaster.data.RagChunk> {
        fun add(catalog: ReferenceCatalog, entry: ReferenceEntry): com.example.netmaster.data.RagChunk {
            val text = listOf(
                entry.summary,
                entry.tags.joinToString(", "),
                entry.layers.joinToString(", "),
                entry.states.joinToString(", "),
                entry.ports.joinToString(", "),
                entry.commands.joinToString("\n"),
                entry.steps.joinToString("\n"),
                entry.observables.joinToString(", "),
                entry.symptom, entry.firstEvidence, entry.discriminatingTest, entry.resolution, entry.rollback,
                entry.sourceUrls.joinToString("\n")
            ).filter { it.isNotBlank() }.joinToString("\n")
            return com.example.netmaster.data.RagChunk(
                id = "reference:${catalog.catalog}:${entry.id}",
                title = entry.title,
                text = text.take(7000),
                tags = entry.tags + "reference:${catalog.catalog}",
                source = "reference/${catalog.catalog}/${entry.id}"
            )
        }
        return buildList {
            bundle.specialistBooks.entries.forEach { entry -> add(add(bundle.specialistBooks, entry)) }
            bundle.protocols.entries.forEach { entry -> add(add(bundle.protocols, entry)) }
            bundle.commands.entries.forEach { entry -> add(add(bundle.commands, entry)) }
            bundle.faults.entries.forEach { entry -> add(add(bundle.faults, entry)) }
            bundle.packetJourneys.entries.forEach { entry -> add(add(bundle.packetJourneys, entry)) }
        }
    }

    suspend fun refresh() {
        _progress.value = dao.progress().associateBy { it.lessonId }
        _notes.value = dao.notes()
        _bookmarks.value = dao.bookmarks().map { it.lessonId }.toSet()
        _incidents.value = dao.incidents()
        _knowledge.value = KnowledgeGraphView(dao.knowledgeNodes(), dao.knowledgeEdges())
        _captures.value = dao.captures()
        _configs.value = dao.allConfigs()
        _dueReviews.value = dao.dueReviews(System.currentTimeMillis(), 30)
        _vectorCount.value = dao.ragVectors().size
    }

    private suspend fun buildVectors(lessons: List<Lesson>, extras: List<com.example.netmaster.data.RagChunk>, persist: Boolean, onProgress: (Int) -> Unit = {}) {
        val count = withContext(Dispatchers.Default) { ragEngine.buildIndex(lessons, extras, onProgress) }
        if (persist) {
            val vectors = ragEngine.vectorEntities()
            withContext(Dispatchers.IO) {
                dao.clearRagVectors()
                dao.ragVectorsBatch(vectors)
            }
        }
        _vectorCount.value = count
    }

    fun rebuildVectorIndex() {
        viewModelScope.launch(Dispatchers.Default) {
            val c = _curriculum.value ?: return@launch
            val content = withContext(Dispatchers.IO) { Pair(repo.loadScenarios(), repo.loadReferences()) }
            val scenarios = content.first
            val refs = content.second
            val started = System.currentTimeMillis()
            _loadState.value = _loadState.value.copy(indexing = true, indexProgress = 0, indexSeconds = 0, stage = "بازسازی ایندکس جستجو…")
            buildVectors(c.levels.flatMap { it.lessons } + DeepContentMapper.scenariosAsLessons(scenarios), referenceRagChunks(refs), true) { progress ->
                val elapsed = ((System.currentTimeMillis() - started) / 1000).toInt()
                _loadState.value = _loadState.value.copy(indexing = true, indexProgress = progress, indexSeconds = elapsed, stage = "بازسازی ایندکس… $progress٪")
            }
            _loadState.value = _loadState.value.copy(indexing = false, indexProgress = 100, stage = "ایندکس جستجو آماده است.")
        }
    }

    fun setMastery(id: String, m: Mastery) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val next = when (m) {
                Mastery.UNKNOWN -> now
                Mastery.REVIEW -> now + ReviewDay * 1
                Mastery.KNOWN -> now + ReviewDay * 3
                Mastery.MASTERED -> now + ReviewDay * 14
            }
            dao.upsertProgress(ProgressEntity(id, m.name, m != Mastery.UNKNOWN, now, next))
            dao.review(if (m == Mastery.UNKNOWN) ReviewItemEntity(id, nextReviewAt = now, lastScore = 0) else reviewEngine.seed(id, m.name, now))
            refresh()
        }
    }

    fun gradeReview(lessonId: String, quality: Int) {
        viewModelScope.launch {
            dao.review(reviewEngine.update(dao.reviewByLesson(lessonId), lessonId, quality))
            val old = _progress.value[lessonId]
            if (old != null) {
                val mastery = when {
                    quality >= 5 -> Mastery.MASTERED
                    quality >= 4 -> Mastery.KNOWN
                    else -> Mastery.REVIEW
                }
                dao.upsertProgress(old.copy(mastery = mastery.name, completed = quality >= 4, nextReviewAt = dao.reviewByLesson(lessonId)?.nextReviewAt ?: System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
            }
            refresh()
        }
    }

    fun addNote(lessonId: String?, title: String, body: String, tags: String = "") {
        viewModelScope.launch {
            dao.insertNote(NoteEntity(lessonId = lessonId, title = title, body = body, tags = tags))
            refresh()
        }
    }

    fun toggleBookmark(id: String) {
        viewModelScope.launch {
            if (_bookmarks.value.contains(id)) dao.unbookmark(id) else dao.bookmark(BookmarkEntity(id))
            refresh()
        }
    }

    fun addSession(minutes: Int) {
        viewModelScope.launch {
            dao.session(StudySessionEntity(startedAt = System.currentTimeMillis(), minutes = minutes))
        }
    }

    fun configureLocalAi(endpoint: String, model: String, protocol: LocalLlmProtocol = LocalLlmProtocol.OLLAMA) {
        localAi = LocalLlmProvider(endpoint.trim(), model.trim(), protocol)
    }

    fun setAiBackend(local: Boolean) {
        activeAi = if (local) localAi else offlineAi
        _aiBackend.value = if (local) "LOCAL LLM" else "OFFLINE"
    }

    fun askAi(mode: AiMode, prompt: String) {
        if (_aiBusy.value) return
        viewModelScope.launch {
            val c = _curriculum.value ?: return@launch
            _aiBusy.value = true
            try {
                val retrieved = withContext(Dispatchers.Default) { ragEngine.retrieve(prompt, 10) }
                val ctx = retrieved.mapNotNull { h ->
                    c.levels.asSequence().flatMap { it.lessons.asSequence() }.firstOrNull { h.id.startsWith(it.id + ":") }
                }.distinctBy { it.id }.take(10)
                val catalogHits = withContext(Dispatchers.Default) { unifiedCatalogSearch.search(prompt, 8) }
                val catalogContext = catalogHits.joinToString("\n---\n") { h -> "${h.kind} | ${h.title} | score=${h.score}\n${h.snippet}" }
                val grounded = prompt + "\n\n[Grounded RAG]\n" + retrieved.joinToString("\n---\n") { h ->
                    "${h.title} | ${h.source} | score=${"%.3f".format(h.score)}\n${h.text.take(2200)}"
                } + "\n\n[Unified Catalog]\n" + catalogContext
                val answer = withTimeoutOrNull(20_000L) { activeAi.ask(mode, grounded, ctx) }
                    ?: AiResponse("AI Timeout", "پاسخ‌گویی طولانی شد. برای جلوگیری از هنگ کردن برنامه، درخواست متوقف شد؛ Offline AI یا سؤال کوتاه‌تر را امتحان کن.")
                _aiResponse.value = answer
            } finally {
                _aiBusy.value = false
            }
        }
    }

    fun createIncident(title: String, symptom: String, severity: String = "MEDIUM") {
        viewModelScope.launch {
            val id = dao.incident(IncidentEntity(title = title, symptom = symptom, severity = severity))
            dao.incidentEvent(IncidentEventEntity(incidentId = id, eventType = "CREATED", message = "Incident created"))
            refresh()
            openIncident(id)
        }
    }

    fun openIncident(id: Long) {
        viewModelScope.launch {
            val i = dao.incidentById(id) ?: return@launch
            val ev = dao.evidence(id)
            val events = dao.incidentEvents(id)
            val capIds = ev.filter { it.type == "CAPTURE" }.mapNotNull { it.value.toLongOrNull() }
            val cfgIds = ev.filter { it.type == "CONFIG" }.mapNotNull { it.value.toLongOrNull() }
            val caps = capIds.mapNotNull { dao.captureById(it) }
            val cfgs = cfgIds.mapNotNull { dao.configById(it) }
            _selectedIncident.value = IncidentWorkspace(i, ev, events, caps, cfgs)
        }
    }

    fun closeIncident(i: IncidentEntity, root: String, resolution: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.updateIncident(i.copy(status = "RESOLVED", rootCause = root, resolution = resolution, updatedAt = now))
            dao.incidentEvent(IncidentEventEntity(incidentId = i.id, eventType = "RESOLVED", message = "Root cause: ${root.take(240)}"))
            openIncident(i.id)
            refresh()
        }
    }

    fun setIncidentStatus(i: IncidentEntity, status: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.updateIncident(i.copy(status = status, updatedAt = now))
            dao.incidentEvent(IncidentEventEntity(incidentId = i.id, eventType = "STATUS", message = status))
            openIncident(i.id)
            refresh()
        }
    }

    fun addIncidentEvidence(id: Long, type: String, value: String) {
        viewModelScope.launch {
            dao.evidence(IncidentEvidenceEntity(incidentId = id, type = type, value = value))
            dao.incidentEvent(IncidentEventEntity(incidentId = id, eventType = "EVIDENCE", message = "$type: ${value.take(240)}"))
            openIncident(id)
            refresh()
        }
    }

    fun linkCaptureToIncident(id: Long, captureId: Long) = addIncidentEvidence(id, "CAPTURE", captureId.toString())
    fun linkConfigToIncident(id: Long, configId: Long) = addIncidentEvidence(id, "CONFIG", configId.toString())

    fun saveConfig(device: String, vendor: String, label: String, text: String) {
        viewModelScope.launch {
            val id = dao.config(ConfigSnapshotEntity(deviceId = device, vendor = vendor, label = label, content = text, fingerprint = configDiffEngine.fingerprint(text)))
            _selectedConfig.value = dao.configById(id)
            refresh()
        }
    }

    fun selectConfig(id: Long) { viewModelScope.launch { _selectedConfig.value = dao.configById(id) } }

    fun importCapture(name: String, raw: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val rows = packetEngine.parse(raw)
            withContext(Dispatchers.IO) {
                dao.capture(PacketCaptureEntity(name = name, format = "TEXT", rawText = raw, packetCount = rows.size, protocolSummary = packetEngine.summary(rows)))
            }
            _packetAnalysis.value = packetEngine.analyze(rows)
            withContext(Dispatchers.Main.immediate) {
                _basePackets.value = rows
                _filteredPackets.value = rows
            }
            refresh()
        }
    }

    fun importPcap(name: String, bytes: ByteArray) {
        viewModelScope.launch(Dispatchers.Default) {
            val started = System.currentTimeMillis()
            _pcapLoad.value = PcapLoadState(busy=true,seconds=0)
            try {
                val rows = pcapParser.parse(bytes)
                if (rows.isEmpty()) {
                    _pcapLoad.value = PcapLoadState(false,((System.currentTimeMillis()-started)/1000).toInt(),"فایل PCAP/PCAPNG قابل‌خواندن نبود یا بسته‌ای نداشت.")
                    return@launch
                }
                val raw = rows.joinToString("\n") { it.toText() }
                val format = if (bytes.size >= 4 && bytes[0].toInt() and 255 == 0x0A) "PCAPNG" else "PCAP"
                withContext(Dispatchers.IO) {
                    dao.capture(PacketCaptureEntity(name = name, format = format, rawText = raw, packetCount = rows.size, protocolSummary = packetEngine.summary(rows)))
                }
                _packetAnalysis.value = packetEngine.analyze(rows)
                withContext(Dispatchers.Main.immediate) {
                    _basePackets.value = rows
                    _filteredPackets.value = rows
                    _pcapLoad.value = PcapLoadState(false,((System.currentTimeMillis()-started)/1000).toInt())
                }
                refresh()
            } catch (t: Throwable) {
                withContext(Dispatchers.Main.immediate) {
                    _pcapLoad.value = PcapLoadState(false,((System.currentTimeMillis()-started)/1000).toInt(),t.message ?: "خطا در تحلیل PCAP")
                }
            }
        }
    }

    fun analyzePacketText(raw:String) {
        viewModelScope.launch(Dispatchers.Default) {
            val rows=packetEngine.parse(raw)
            _packetAnalysis.value=packetEngine.analyze(rows)
            _basePackets.value=rows
            _filteredPackets.value=rows
        }
    }

    fun compareConfigs(old:String,new:String) {
        viewModelScope.launch(Dispatchers.Default) {
            _configDiff.value=configDiffEngine.summarize(old,new)
        }
    }

    fun loadCapture(id: Long) {
        viewModelScope.launch(Dispatchers.Default) {
            val c = withContext(Dispatchers.IO) { dao.captureById(id) } ?: return@launch
            val rows = packetEngine.parse(c.rawText)
            _packetAnalysis.value = packetEngine.analyze(rows)
            _basePackets.value = rows
            _filteredPackets.value = rows
            _filterError.value = null
        }
    }

    fun filterCapture(expression: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val r = packetFilterEngine.filter(_basePackets.value, expression)
            _filterError.value = r.error
            if (r.error == null) _filteredPackets.value = r.records
        }
    }

    fun resetPacketFilter() {
        _filteredPackets.value = _basePackets.value
        _filterError.value = null
    }

    fun buildKnowledgeGraph() {
        viewModelScope.launch(Dispatchers.Default) {
            val c = _curriculum.value ?: return@launch
            val nodes = mutableListOf<KnowledgeNodeEntity>()
            val edges = mutableListOf<KnowledgeEdgeEntity>()
            c.levels.forEach { level ->
                level.lessons.forEach { l ->
                    nodes += KnowledgeNodeEntity(l.id, l.title, "LESSON", l.technical, l.tags.joinToString(","))
                    l.tags.distinct().forEach { tag ->
                        val tagId = "tag:" + tag.lowercase()
                        nodes += KnowledgeNodeEntity(tagId, tag, "TAG", tag)
                        edges += KnowledgeEdgeEntity(l.id, tagId, "TAGGED_WITH")
                    }
                }
            }
            withContext(Dispatchers.IO) {
                dao.knowledgeNodesBatch(nodes.distinctBy { it.id })
                dao.knowledgeEdgesBatch(edges.distinctBy { listOf(it.fromId, it.toId, it.relation) })
            }
            refresh()
        }
    }

    fun injectFault(f: TwinFault) {
        _twin.value = twinEngine.inject(_twin.value, f)
        _twinVerification.value = null
        _coach.value = null
    }

    fun repairTwin() {
        _twin.value = twinEngine.repair(_twin.value)
        _twinVerification.value = twinEngine.verify(_twin.value)
    }

    fun verifyTwin() {
        _twinVerification.value = twinEngine.verify(_twin.value)
    }

    fun completeTwin() {
        _twin.value = twinEngine.complete(_twin.value)
        _twinVerification.value = twinEngine.verify(_twin.value)
    }

    fun resetTwin() {
        _twin.value = twinEngine.defaultState()
        _twinVerification.value = null
        _coach.value = null
        _simOutput.value = ""
    }

    /** Switch live Digital Twin to a named multi-device topology preset. */
    fun loadTopologyPreset(presetId: String) {
        val preset = PacketPathEngine.presets().firstOrNull { it.id == presetId } ?: return
        val base = commandSimulator.defaultState().copy(nodes = preset.nodes, links = preset.links)
        _twin.value = TwinState(nodes = preset.nodes, links = preset.links, simulator = base)
        _coach.value = null
        _simOutput.value = "توپولوژی «${preset.title}» بارگذاری شد — ${preset.nodes.size} دیوایس"
    }

    fun recordEvidence(e: String) {
        _twin.value = twinEngine.recordEvidence(_twin.value, e)
    }

    fun runSimulator(command: String) {
        val state = _twin.value.simulator ?: commandSimulator.defaultState()
        val r = commandSimulator.execute(state, command)
        _simOutput.value = r.output
        if (r.changed) _twin.value = _twin.value.copy(simulator = r.state, nodes = r.state.nodes, links = r.state.links)
    }

    fun saveSimulatorSnapshot(label: String) {
        viewModelScope.launch {
            val state = _twin.value.simulator ?: commandSimulator.defaultState()
            dao.simulatorSnapshot(SimulatorSnapshotEntity(label = label, stateJson = SimulatorCodec.encode(state)))
            refresh()
        }
    }

    fun startCoach(symptom: String) {
        val state = _twin.value.simulator ?: commandSimulator.defaultState()
        val c = coachEngine.start(symptom, state, _filteredPackets.value)
        _coach.value = c
        viewModelScope.launch {
            dao.coachRun(CoachRunEntity(symptom = c.symptom, scenarioId = c.scenarioId, status = c.status, transcript = "START\n${c.hypotheses.joinToString(" | ") { it.title }}"))
        }
    }

    fun coachNext() {
        val c = _coach.value ?: return
        val state = _twin.value.simulator ?: commandSimulator.defaultState()
        val (next, r) = coachEngine.executeNext(c, state)
        _coach.value = next
        if (r != null) {
            _simOutput.value = r.output
            _twin.value = _twin.value.copy(simulator = r.state)
        }
        viewModelScope.launch {
            dao.coachRun(CoachRunEntity(symptom = next.symptom, scenarioId = next.scenarioId, status = next.status, transcript = next.evidence.takeLast(8).joinToString("\n")))
        }
    }

    fun searchAll(query: String) {
        viewModelScope.launch {
            val local = globalSearchEngine.search(
                _curriculum.value?.levels?.flatMap { it.lessons }.orEmpty(),
                _notes.value, _incidents.value, _configs.value, _captures.value, query
            )
            val catalog = unifiedCatalogSearch.search(query)
            _searchResults.value = (local + catalog).groupBy { it.kind + ":" + it.id }.values.map { it.maxBy { h -> h.score } }.sortedByDescending { it.score }.take(50)
        }
    }

    fun investigateEngineering(symptom: String, evidence: String) {
        viewModelScope.launch {
            val c = _curriculum.value ?: return@launch
            _engineeringInvestigation.value = engineeringEngine.investigate(symptom, evidence, c.levels.flatMap { it.lessons }, _filteredPackets.value)
        }
    }

    fun computeEngineeringPassport() {
        viewModelScope.launch {
            val c = _curriculum.value ?: return@launch
            val resolved = _incidents.value.count { it.status == "RESOLVED" }
            _engineeringPassport.value = engineeringEngine.passport(_progress.value, resolved, _captures.value.size, c.levels.flatMap { it.lessons })
        }
    }

    fun injectEngineeringFault(id: String) {
        val f = engineeringEngine.faultCatalog().firstOrNull { it.id == id } ?: return
        _engineeringSelectedFault.value = f
        val mapped = when (id) {
            "eng-dhcp-offer" -> "dhcp"
            "eng-dns-servfail" -> "dns"
            "eng-ospf-mtu" -> "ospf"
            "eng-vlan-allow" -> "vlan"
            "eng-tcp-mss" -> "mtu"
            "eng-fw-order" -> "firewall"
            "eng-nat-state" -> "nat"
            "eng-stp-root" -> "stp"
            else -> ""
        }
        vmFault(mapped)
    }

    private fun vmFault(id: String) {
        if (id.isBlank()) return
        val tf = twinEngine.faults().firstOrNull { it.id == id } ?: return
        _twin.value = twinEngine.inject(_twin.value, tf)
        _coach.value = null
    }

    fun clearEngineering() {
        _engineeringInvestigation.value = null
        _engineeringSelectedFault.value = null
        computeEngineeringPassport()
    }

    fun exportBackup() {
        viewModelScope.launch {
            _exported.value = ExportManager.createBackup(
                getApplication(), _curriculum.value, _progress.value.values.toList(), _notes.value,
                _bookmarks.value.map { BookmarkEntity(it) }, incidents = _incidents.value,
                evidence = dao.allIncidentEvidence(), configs = _configs.value, captures = _captures.value,
                reviews = dao.allReviews(), vectors = dao.ragVectors(), knowledgeNodes = dao.knowledgeNodes(),
                knowledgeEdges = dao.knowledgeEdges(), simulatorSnapshots = dao.simulatorSnapshots(),
                coachRuns = dao.coachRuns(), events = dao.allIncidentEvents()
            )
        }
    }

    private fun PacketRecord.toText() = "${number}\t${timestamp}\t${source}\t${destination}\t${protocol}\t${info}"

    fun login(username: String, password: String): Boolean {
        val okUser = username.trim() == AUTH_USERNAME
        val okPassword = verifyPassword(password)
        val ok = okUser && okPassword
        authPrefs.edit().putBoolean(KEY_SESSION, ok).apply()
        _authenticated.value = ok
        return ok
    }

    fun logout() {
        authPrefs.edit().putBoolean(KEY_SESSION, false).apply()
        _authenticated.value = false
    }

    private fun verifyPassword(password: String): Boolean {
        return try {
            val salt = Base64.decode(AUTH_SALT_B64, Base64.DEFAULT)
            val expected = Base64.decode(AUTH_HASH_B64, Base64.DEFAULT)
            val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
            val actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            spec.clearPassword()
            MessageDigest.isEqual(actual, expected)
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val ReviewDay = 86_400_000L
        private const val KEY_SESSION = "authenticated"
        private const val KEY_RAG_CONTENT_VERSION = "rag_content_version"
        private const val RAG_CONTENT_VERSION = "netmaster-1.0.0-complete-content-2026-10-r4"
        private const val AUTH_USERNAME = "09132184122"
        private const val AUTH_SALT_B64 = "TmV0TWFzdGVyTGVhcm5pbmctdjMtYXV0aC1zYWx0"
        private const val AUTH_HASH_B64 = "t9aAWRaQwL8tQCwIllr1J1753Yj3zrsdtyiR3FHx0ss="
    }
}
