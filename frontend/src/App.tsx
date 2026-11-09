import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import {
  ArrowDownRight,
  ArrowLeft,
  ArrowRight,
  BookOpen,
  CalendarDays,
  Check,
  ChevronRight,
  CircleHelp,
  Clock3,
  GraduationCap,
  LayoutDashboard,
  ListChecks,
  LogOut,
  Plus,
  Search,
  Sparkles,
  UsersRound,
  X,
} from "lucide-react";
import { formatTimeRange } from "./formatTimeRange";

type User = {
  id: number;
  name: string;
  username: string;
  role: "COUNSELOR" | "TEACHER";
};
type Student = {
  id: number;
  name: string;
  status: string;
  counselorId: number;
  counselorName: string;
  mine: number;
};
type Trial = {
  id: number;
  studentId: number;
  studentName: string;
  teacherId: number;
  teacherName: string;
  startsAt: string;
  endsAt: string;
  status: "SCHEDULED" | "COMPLETED" | "CANCELLED";
  result: string | null;
  notes: string | null;
  noFollowupReason: string | null;
};
type FollowUp = {
  id: number;
  studentId: number;
  studentName: string;
  trialId: number;
  dueAt: string;
  status: "OPEN" | "DONE";
  action: string;
  completionNote: string | null;
};
type Dashboard = {
  studentCount: number;
  trials: Trial[];
  pendingResults: Trial[];
  followups: FollowUp[];
};
type Detail = {
  id: number;
  name: string;
  status: string;
  contacts: { name: string; phone: string; relationship: string }[];
  trials: Trial[];
  followups: FollowUp[];
};
type Teacher = { id: number; name: string };
type Lesson = {
  type: string;
  title: string;
  startsAt: string;
  endsAt: string;
  students?: string[];
};
type Suggestion = {
  available: boolean;
  message?: string;
  summary?: string;
  suggestedAction?: string;
};
type Dialog =
  | { type: "student" }
  | { type: "schedule"; student: Detail; trial?: Trial }
  | { type: "result"; student: Detail; trial: Trial }
  | { type: "followup"; followup: FollowUp }
  | null;

async function api<T>(
  path: string,
  method = "GET",
  body?: unknown,
): Promise<T> {
  const res = await fetch("/api" + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      "X-Requested-With": "XMLHttpRequest",
    },
    credentials: "same-origin",
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `请求失败 (${res.status})`);
  return data as T;
}
const resultText: Record<string, string> = {
  INTERESTED: "有报名意向",
  UNDECIDED: "考虑中",
  NOT_INTERESTED: "暂无意向",
};
const display = (v: string) => v.replace("T", " ");
function melbourneToday() {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Australia/Melbourne",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}
function melbourneNow() {
  return new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Australia/Melbourne",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  })
    .format(new Date())
    .replace(" ", "T");
}
function niceDate() {
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Australia/Melbourne",
    year: "numeric",
    month: "long",
    day: "numeric",
    weekday: "long",
  }).format(new Date());
}
function dateLabel(date: string) {
  const d = date.slice(0, 10);
  if (d === melbourneToday()) return "今天 " + date.slice(11, 16);
  return display(date);
}

export default function App() {
  const [user, setUser] = useState<User | null>(null),
    [checking, setChecking] = useState(true),
    [view, setView] = useState<"home" | "students" | "schedule">("home");
  const [dashboard, setDashboard] = useState<Dashboard | null>(null),
    [students, setStudents] = useState<Student[]>([]),
    [teachers, setTeachers] = useState<Teacher[]>([]),
    [lessons, setLessons] = useState<Lesson[]>([]);
  const [detail, setDetail] = useState<Detail | null>(null),
    [dialog, setDialog] = useState<Dialog>(null),
    [loading, setLoading] = useState(false),
    [error, setError] = useState(""),
    [query, setQuery] = useState("");
  const [refreshCount, setRefreshCount] = useState(0);
  const reload = () => setRefreshCount((n) => n + 1);
  useEffect(() => {
    api<User>("/me")
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setChecking(false));
  }, []);
  useEffect(() => {
    if (!user) return;
    let active = true;
    setLoading(true);
    setError("");
    const request =
      user.role === "TEACHER"
        ? api<Lesson[]>("/teacher/today").then((l) => {
            if (active) setLessons(l);
          })
        : Promise.all([
            api<Dashboard>("/dashboard"),
            api<Student[]>("/students?q=" + encodeURIComponent(query)),
            api<Teacher[]>("/teachers"),
          ]).then(([d, s, t]) => {
            if (active) {
              setDashboard(d);
              setStudents(s);
              setTeachers(t);
            }
          });
    request
      .catch((e) => {
        if (active) setError(e.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [user, refreshCount, query]);
  useEffect(() => {
    if (!user || user.role !== "COUNSELOR") return;
    const timer = window.setInterval(() => {
      if (document.visibilityState === "visible") setRefreshCount((n) => n + 1);
    }, 60_000);
    const refreshOnReturn = () => {
      if (document.visibilityState === "visible") setRefreshCount((n) => n + 1);
    };
    document.addEventListener("visibilitychange", refreshOnReturn);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", refreshOnReturn);
    };
  }, [user]);
  const openDetail = useCallback(async (id: number) => {
    setError("");
    setLoading(true);
    try {
      setDetail(await api<Detail>(`/students/${id}`));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);
  const refreshDetail = useCallback(async (id: number) => {
    setDetail(await api<Detail>(`/students/${id}`));
    reload();
  }, []);
  const open = (id: number) => {
    void openDetail(id);
  };
  const openTrialResult = async (trial: Trial) => {
    setError("");
    setLoading(true);
    try {
      const student = await api<Detail>(`/students/${trial.studentId}`);
      const latestTrial = student.trials.find((item) => item.id === trial.id);
      if (!latestTrial || latestTrial.status !== "SCHEDULED") {
        reload();
        throw new Error("试听状态已更新，请刷新后查看。");
      }
      setDialog({ type: "result", student, trial: latestTrial });
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  };
  const logout = async () => {
    await api("/logout", "POST").catch(() => {});
    setUser(null);
    setDetail(null);
    setDashboard(null);
    setView("home");
  };
  const onSaved = async (studentId?: number) => {
    setDialog(null);
    if (studentId) await refreshDetail(studentId);
    else reload();
  };
  const overdue = useMemo(
    () =>
      dashboard?.followups.filter((f) => f.dueAt < melbourneNow()).length ?? 0,
    [dashboard],
  );
  if (checking)
    return (
      <div className="full-state">
        <div className="spinner" />
        正在打开工作台…
      </div>
    );
  if (!user) return <Login onLogin={setUser} />;
  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">
            <GraduationCap size={22} />
          </div>
          <div>
            <strong>STUDENT DESK</strong>
            <span>学生管理工作台</span>
          </div>
        </div>
        <div className="side-section">WORKSPACE</div>
        <nav aria-label="主导航">
          <button
            className={view === "home" ? "nav-item active" : "nav-item"}
            onClick={() => {
              setView("home");
              setDetail(null);
            }}
          >
            <LayoutDashboard size={18} /> 工作台 <ChevronRight size={15} />
          </button>
          {user.role === "COUNSELOR" && (
            <>
              <button
                className={view === "students" ? "nav-item active" : "nav-item"}
                onClick={() => {
                  setView("students");
                  setDetail(null);
                }}
              >
                <UsersRound size={18} /> 我的学生
              </button>
              <button
                className={view === "schedule" ? "nav-item active" : "nav-item"}
                onClick={() => {
                  setView("schedule");
                  setDetail(null);
                }}
              >
                <CalendarDays size={18} /> 试听排期
              </button>
            </>
          )}
        </nav>
        <div className="side-bottom">
          <div className="side-help">
            <CircleHelp size={17} />
            <div>
              <strong>需要帮助？</strong>
              <span>每一步，都从关心开始。</span>
            </div>
          </div>
          <button className="user-card" onClick={logout} title="退出登录">
            <span className="avatar">{user.name.slice(0, 1)}</span>
            <span className="user-info">
              <strong>{user.name}</strong>
              <small>
                {user.role === "COUNSELOR" ? "课程顾问" : "授课老师"}
              </small>
            </span>
            <LogOut size={16} />
          </button>
        </div>
      </aside>
      <main className="main">
        <header className="topbar">
          <div className="breadcrumb">
            工作空间 <ChevronRight size={14} />{" "}
            <strong>
              {detail
                ? "学生详情"
                : view === "students"
                  ? "学生管理"
                  : view === "schedule"
                    ? "试听排期"
                    : "工作台"}
            </strong>
          </div>
          <div className="top-right">
            <span className="timezone">
              <span className="online-dot" /> 澳大利亚 · 墨尔本时间
            </span>
            <span className="avatar small">{user.name.slice(0, 1)}</span>
            <button
              className="icon-button mobile-logout"
              onClick={logout}
              aria-label="退出登录"
            >
              <LogOut size={17} />
            </button>
          </div>
        </header>
        <div className="content">
          {error && (
            <div className="notice error" role="alert">
              {error}
              <button onClick={() => setError("")} aria-label="关闭">
                <X size={16} />
              </button>
            </div>
          )}
          {loading && <div className="loading-line">正在加载最新数据…</div>}
          {user.role === "TEACHER" ? (
            <TeacherHome user={user} lessons={lessons} loading={loading} />
          ) : detail ? (
            <StudentDetail
              detail={detail}
              onBack={() => setDetail(null)}
              onDialog={setDialog}
              onCancel={async (t) => {
                try {
                  await api(`/trials/${t.id}/cancel`, "POST");
                  await refreshDetail(detail.id);
                } catch (e) {
                  setError((e as Error).message);
                }
              }}
            />
          ) : view === "home" ? (
            <>
              <div className="heading-row">
                <div>
                  <div className="eyebrow">
                    <span className="online-dot" /> {niceDate()}
                  </div>
                  <h1>
                    你好，{user.name} <span className="wave">✳</span>
                  </h1>
                  <p className="subtitle">
                    每一次及时的跟进，都是一段好关系的开始。
                  </p>
                </div>
                <button
                  className="button primary"
                  onClick={() => setDialog({ type: "student" })}
                >
                  <Plus size={17} /> 新建学生
                </button>
              </div>
              <div className="metrics">
                <Metric
                  icon={<UsersRound size={19} />}
                  label="我负责的学生"
                  value={dashboard?.studentCount ?? 0}
                  sub="持续陪伴每一位学生"
                  tone="green"
                />
                <Metric
                  icon={<ListChecks size={19} />}
                  label="待跟进"
                  value={dashboard?.followups.length ?? 0}
                  sub={
                    overdue
                      ? `${overdue} 项已到期，记得及时联系`
                      : "所有跟进都在掌握中"
                  }
                  tone="amber"
                />
                <Metric
                  icon={<CalendarDays size={19} />}
                  label="已安排试听"
                  value={
                    (dashboard?.trials.length ?? 0) +
                    (dashboard?.pendingResults.length ?? 0)
                  }
                  sub={
                    dashboard?.pendingResults.length
                      ? `${dashboard.pendingResults.length} 项已结束，待录入结果`
                      : "按时间顺序查看排期"
                  }
                  tone="blue"
                />
              </div>
              {!!dashboard?.pendingResults.length && (
                <>
                  <div className="section-head">
                    <div>
                      <div className="overline">RESULTS NEEDED</div>
                      <h2>
                        试听结果待录入{" "}
                        <span className="count">
                          {dashboard.pendingResults.length}
                        </span>
                      </h2>
                      <p>这些试听已结束，请及时记录结果和下一步。</p>
                    </div>
                  </div>
                  <div className="card">
                    {dashboard.pendingResults.map((trial) => (
                      <div className="trial-row" key={trial.id}>
                        <span className="avatar pale">
                          {trial.studentName.slice(0, 1)}
                        </span>
                        <div className="trial-info">
                          <strong>
                            {trial.studentName} · {trial.teacherName}
                          </strong>
                          <span>
                            {formatTimeRange(trial.startsAt, trial.endsAt)}
                          </span>
                        </div>
                        <Badge tone="orange">待录入结果</Badge>
                        <button
                          className="row-action"
                          disabled={loading}
                          onClick={() => void openTrialResult(trial)}
                        >
                          记录结果 <ArrowRight size={14} />
                        </button>
                      </div>
                    ))}
                  </div>
                </>
              )}
              <div className="section-head">
                <div>
                  <div className="overline">YOUR PRIORITIES</div>
                  <h2>
                    待办与跟进{" "}
                    <span className="count">
                      {dashboard?.followups.length ?? 0}
                    </span>
                  </h2>
                  <p>优先处理需要联系的学生，别让一次试听停在这里。</p>
                </div>
                <button
                  className="text-button"
                  onClick={() => {
                    setView("students");
                  }}
                >
                  查看所有学生 <ArrowRight size={15} />
                </button>
              </div>
              <div className="card table-card">
                {!dashboard?.followups.length && !loading ? (
                  <Empty
                    text="暂时没有待跟进事项"
                    hint="试听完成并创建跟进后，会出现在这里。"
                  />
                ) : (
                  <>
                    <div className="table-head follow-grid">
                      <span>学生</span>
                      <span>跟进内容</span>
                      <span>计划联系</span>
                      <span>状态</span>
                      <span />
                    </div>
                    {dashboard?.followups.map((f) => (
                      <div className="table-row follow-grid" key={f.id}>
                        <button
                          className="person-link"
                          onClick={() => open(f.studentId)}
                        >
                          <span className="avatar pale">
                            {f.studentName.slice(0, 1)}
                          </span>
                          <span>
                            <strong>{f.studentName}</strong>
                            <small>试听后跟进</small>
                          </span>
                        </button>
                        <span className="muted">{f.action}</span>
                        <span className="date-cell">
                          <Clock3 size={15} /> {dateLabel(f.dueAt)}
                        </span>
                        <span>
                          <Badge
                            tone={f.dueAt < melbourneNow() ? "orange" : "green"}
                          >
                            {f.dueAt < melbourneNow() ? "需跟进" : "待处理"}
                          </Badge>
                        </span>
                        <button
                          className="row-action"
                          onClick={() =>
                            setDialog({ type: "followup", followup: f })
                          }
                        >
                          处理 <ArrowRight size={14} />
                        </button>
                      </div>
                    ))}
                  </>
                )}
              </div>
              <div className="section-head bottom-head">
                <div>
                  <div className="overline">COMING UP</div>
                  <h2>即将及进行中的试听</h2>
                  <p>提前了解安排，让每一次初见都从容。</p>
                </div>
                <button
                  className="text-button"
                  onClick={() => setView("schedule")}
                >
                  查看全部排期 <ArrowRight size={15} />
                </button>
              </div>
              <TrialList
                trials={dashboard?.trials.slice(0, 4) ?? []}
                onOpen={open}
                empty="还没有安排试听"
              />
            </>
          ) : view === "students" ? (
            <>
              <div className="heading-row">
                <div>
                  <div className="overline">STUDENT DIRECTORY</div>
                  <h1>学生管理</h1>
                  <p className="subtitle">找到学生，了解每一段学习旅程。</p>
                </div>
                <button
                  className="button primary"
                  onClick={() => setDialog({ type: "student" })}
                >
                  <Plus size={17} /> 新建学生
                </button>
              </div>
              <div className="card directory">
                <div className="directory-top">
                  <div>
                    <h2>学生列表</h2>
                    <p>其他顾问的学生仅展示基本信息</p>
                  </div>
                  <label className="search">
                    <Search size={17} />
                    <input
                      value={query}
                      onChange={(e) => setQuery(e.target.value)}
                      placeholder="搜索学生姓名"
                    />
                  </label>
                </div>
                {students.length === 0 && !loading ? (
                  <Empty
                    text="没有找到学生"
                    hint="试试其他关键词，或新建学生。"
                  />
                ) : (
                  students.map((s) => (
                    <div className="student-row" key={s.id}>
                      <div className="person-link">
                        <span className="avatar pale">
                          {s.name.slice(0, 1)}
                        </span>
                        <span>
                          <strong>{s.name}</strong>
                          <small>负责人：{s.counselorName}</small>
                        </span>
                      </div>
                      <Badge tone={s.mine ? "green" : "gray"}>
                        {s.mine ? "我负责" : "其他顾问"}
                      </Badge>
                      {!!s.mine ? (
                        <button
                          className="row-action"
                          onClick={() => open(s.id)}
                        >
                          查看详情 <ArrowRight size={14} />
                        </button>
                      ) : (
                        <span className="muted tiny">仅基本信息</span>
                      )}
                    </div>
                  ))
                )}
              </div>
            </>
          ) : (
            <>
              <div className="heading-row">
                <div>
                  <div className="overline">YOUR SCHEDULE</div>
                  <h1>试听排期</h1>
                  <p className="subtitle">查看并管理你负责学生的试听安排。</p>
                </div>
              </div>
              <TrialList
                trials={
                  dashboard
                    ? [...dashboard.pendingResults, ...dashboard.trials].sort(
                        (a, b) => a.startsAt.localeCompare(b.startsAt),
                      )
                    : []
                }
                pendingTrialIds={
                  new Set(
                    dashboard?.pendingResults.map((trial) => trial.id) ?? [],
                  )
                }
                onOpen={open}
                empty="暂时没有试听安排"
              />
            </>
          )}
        </div>
      </main>
      {dialog && (
        <DialogForm
          dialog={dialog}
          teachers={teachers}
          onClose={() => setDialog(null)}
          onSaved={onSaved}
        />
      )}
    </div>
  );
}

function Metric({
  icon,
  label,
  value,
  sub,
  tone,
}: {
  icon: ReactNode;
  label: string;
  value: number;
  sub: string;
  tone: string;
}) {
  return (
    <div className="metric card">
      <div className="metric-top">
        <span className={"metric-icon " + tone}>{icon}</span>
        <ArrowDownRight size={17} className="metric-arrow" />
      </div>
      <span className="metric-label">{label}</span>
      <div className="metric-value">{String(value).padStart(2, "0")}</div>
      <span className="metric-sub">{sub}</span>
    </div>
  );
}
function Badge({
  children,
  tone = "green",
}: {
  children: ReactNode;
  tone?: string;
}) {
  return (
    <span className={"badge " + tone}>
      <span /> {children}
    </span>
  );
}
function Empty({ text, hint }: { text: string; hint: string }) {
  return (
    <div className="empty">
      <div className="empty-icon">
        <BookOpen size={22} />
      </div>
      <strong>{text}</strong>
      <span>{hint}</span>
    </div>
  );
}
function TrialList({
  trials,
  onOpen,
  empty,
  pendingTrialIds,
}: {
  trials: Trial[];
  onOpen: (id: number) => void;
  empty: string;
  pendingTrialIds?: ReadonlySet<number>;
}) {
  return (
    <div className="card">
      {trials.length === 0 ? (
        <Empty text={empty} hint="在学生详情中可以安排试听。" />
      ) : (
        trials.map((t) => (
          <div className="trial-row" key={t.id}>
            <div className="date-block">
              <strong>{t.startsAt.slice(8, 10)}</strong>
              <small>{Number(t.startsAt.slice(5, 7))}月</small>
            </div>
            <div className="trial-info">
              <strong>{t.studentName} · 试听课</strong>
              <span>
                {t.teacherName} <i /> {formatTimeRange(t.startsAt, t.endsAt)}
              </span>
            </div>
            <Badge tone={pendingTrialIds?.has(t.id) ? "orange" : "blue"}>
              {pendingTrialIds?.has(t.id) ? "待录入结果" : "已安排"}
            </Badge>
            <button
              className="icon-button"
              onClick={() => onOpen(t.studentId)}
              aria-label={"查看" + t.studentName}
            >
              <ChevronRight size={18} />
            </button>
          </div>
        ))
      )}
    </div>
  );
}
function TeacherHome({
  user,
  lessons,
  loading,
}: {
  user: User;
  lessons: Lesson[];
  loading: boolean;
}) {
  return (
    <>
      <div className="heading-row">
        <div>
          <div className="eyebrow">
            <span className="online-dot" /> {niceDate()}
          </div>
          <h1>
            你好，{user.name} <span className="wave">✳</span>
          </h1>
          <p className="subtitle">今天的安排都在这里，祝你上课愉快。</p>
        </div>
      </div>
      <div className="section-head">
        <div>
          <div className="overline">TODAY'S CLASSES</div>
          <h2>
            今日课程 <span className="count">{lessons.length}</span>
          </h2>
          <p>仅展示分配给你的试听和正式课。</p>
        </div>
      </div>
      <div className="card">
        {!lessons.length && !loading ? (
          <Empty text="今天没有课程安排" hint="享受有余裕的一天。" />
        ) : (
          lessons.map((l, i) => (
            <div className="trial-row" key={i}>
              <div className="date-block">
                <strong>{l.startsAt.slice(11, 16)}</strong>
              </div>
              <div className="trial-info">
                <strong>{l.title}</strong>
                <span>
                  {l.type} · {formatTimeRange(l.startsAt, l.endsAt)}
                  {l.students?.length
                    ? ` · 学生：${l.students.join("、")}`
                    : ""}
                </span>
              </div>
              <Badge tone="blue">{l.type}</Badge>
            </div>
          ))
        )}
      </div>
    </>
  );
}
function StudentDetail({
  detail,
  onBack,
  onDialog,
  onCancel,
}: {
  detail: Detail;
  onBack: () => void;
  onDialog: (d: Dialog) => void;
  onCancel: (t: Trial) => void;
}) {
  return (
    <>
      <button className="back-button" onClick={onBack}>
        <ArrowLeft size={16} /> 返回列表
      </button>
      <div className="heading-row detail-heading">
        <div className="detail-person">
          <span className="avatar large">{detail.name.slice(0, 1)}</span>
          <div>
            <div className="overline">STUDENT PROFILE</div>
            <h1>{detail.name}</h1>
            <p className="subtitle">咨询中 · 你负责的学生</p>
          </div>
        </div>
        <button
          className="button primary"
          onClick={() => onDialog({ type: "schedule", student: detail })}
        >
          <Plus size={17} /> 安排试听
        </button>
      </div>
      <div className="profile-grid">
        <div>
          <div className="section-head compact">
            <div>
              <div className="overline">TRIAL CLASSES</div>
              <h2>试听记录</h2>
            </div>
          </div>
          <div className="card">
            {detail.trials.length === 0 ? (
              <Empty text="还没有试听记录" hint="点击右上角安排第一节试听。" />
            ) : (
              detail.trials.map((t) => (
                <div className="detail-item" key={t.id}>
                  <div className="item-title">
                    <strong>{t.teacherName} · 试听课</strong>
                    <Badge
                      tone={
                        t.status === "SCHEDULED"
                          ? "blue"
                          : t.status === "COMPLETED"
                            ? "green"
                            : "gray"
                      }
                    >
                      {t.status === "SCHEDULED"
                        ? "已安排"
                        : t.status === "COMPLETED"
                          ? "已完成"
                          : "已取消"}
                    </Badge>
                  </div>
                  <p>
                    <Clock3 size={14} /> {formatTimeRange(t.startsAt, t.endsAt)}
                  </p>
                  {t.result && (
                    <p>
                      结果：{resultText[t.result] || t.result}{" "}
                      {t.notes && `· ${t.notes}`}
                    </p>
                  )}
                  {t.noFollowupReason && <p>无需跟进：{t.noFollowupReason}</p>}
                  {t.status === "SCHEDULED" && (
                    <div className="actions">
                      <button
                        className="button secondary small-btn"
                        onClick={() =>
                          onDialog({
                            type: "result",
                            student: detail,
                            trial: t,
                          })
                        }
                      >
                        记录结果
                      </button>
                      <button
                        className="button ghost small-btn"
                        onClick={() =>
                          onDialog({
                            type: "schedule",
                            student: detail,
                            trial: t,
                          })
                        }
                      >
                        调整
                      </button>
                      <button
                        className="button ghost danger small-btn"
                        onClick={() => onCancel(t)}
                      >
                        取消试听
                      </button>
                    </div>
                  )}
                </div>
              ))
            )}
          </div>
          <div className="section-head compact">
            <div>
              <div className="overline">FOLLOW-UP</div>
              <h2>跟进记录</h2>
            </div>
          </div>
          <div className="card">
            {detail.followups.length === 0 ? (
              <Empty
                text="暂无跟进记录"
                hint="完成试听时可以创建下一步跟进。"
              />
            ) : (
              detail.followups.map((f) => (
                <div className="detail-item" key={f.id}>
                  <div className="item-title">
                    <strong>{f.action}</strong>
                    <Badge tone={f.status === "OPEN" ? "orange" : "green"}>
                      {f.status === "OPEN" ? "待跟进" : "已完成"}
                    </Badge>
                  </div>
                  <p>
                    <Clock3 size={14} /> 计划联系：{display(f.dueAt)}
                  </p>
                  {f.completionNote && <p>完成记录：{f.completionNote}</p>}
                  {f.status === "OPEN" && (
                    <button
                      className="button secondary small-btn"
                      onClick={() =>
                        onDialog({ type: "followup", followup: f })
                      }
                    >
                      完成跟进
                    </button>
                  )}
                </div>
              ))
            )}
          </div>
        </div>
        <aside>
          <div className="section-head compact">
            <div>
              <div className="overline">CONTACT</div>
              <h2>联系人</h2>
            </div>
          </div>
          <div className="card contact-card">
            {detail.contacts.map((c, i) => (
              <div key={i}>
                <span className="contact-label">{c.relationship}</span>
                <strong>{c.name}</strong>
                <span>{c.phone}</span>
              </div>
            ))}
          </div>
          <div className="tip-card">
            <Sparkles size={18} />
            <strong>每一次联系都很重要</strong>
            <p>试听结束后，及时记录结果与下一步，帮助学生顺利开启学习旅程。</p>
          </div>
        </aside>
      </div>
    </>
  );
}
function Login({ onLogin }: { onLogin: (u: User) => void }) {
  const [username, setUsername] = useState("counselor"),
    [password, setPassword] = useState("demo1234"),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      onLogin(await api<User>("/login", "POST", { username, password }));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="login-page">
      <div className="login-panel">
        <div className="brand login-brand">
          <div className="brand-mark">
            <GraduationCap size={22} />
          </div>
          <div>
            <strong>STUDENT DESK</strong>
            <span>学生管理工作台</span>
          </div>
        </div>
        <div className="login-content">
          <div className="overline">WELCOME BACK</div>
          <h1>
            让每一次
            <br />
            连接都有回响<span className="wave">.</span>
          </h1>
          <p>
            从第一次咨询到每一次跟进，
            <br />
            用更从容的方式陪伴学生成长。
          </p>
          <div className="login-art">
            <span>✳</span>
            <div>
              <strong>Every student matters.</strong>
              <small>A little care goes a long way.</small>
            </div>
          </div>
        </div>
      </div>
      <div className="login-form-area">
        <form className="login-form" onSubmit={submit}>
          <div className="overline">YOUR WORKSPACE</div>
          <h2>欢迎回来</h2>
          <p>登录你的学生管理工作台</p>
          {error && (
            <div className="notice error" role="alert">
              {error}
            </div>
          )}
          <label>
            账号
            <input
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </label>
          <label>
            密码
            <input
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>
          <button className="button primary login-button" disabled={busy}>
            {busy ? "正在登录…" : "登录工作台"} <ArrowRight size={17} />
          </button>
          <div className="demo-info">
            <strong>演示账号</strong>
            <span>顾问 counselor / demo1234</span>
            <span>另一位顾问 counselor2 / demo1234</span>
            <span>老师 teacher / demo1234</span>
          </div>
          <small className="login-foot">
            业务时间统一使用 Australia/Melbourne
          </small>
        </form>
      </div>
    </div>
  );
}

function DialogForm({
  dialog,
  teachers,
  onClose,
  onSaved,
}: {
  dialog: NonNullable<Dialog>;
  teachers: Teacher[];
  onClose: () => void;
  onSaved: (studentId?: number) => Promise<void>;
}) {
  const [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [aiBusy, setAiBusy] = useState(false),
    [suggestion, setSuggestion] = useState<Suggestion | null>(null);
  const [name, setName] = useState(""),
    [contactName, setContactName] = useState(""),
    [phone, setPhone] = useState(""),
    [relationship, setRelationship] = useState("家长");
  const [teacher, setTeacher] = useState(
    dialog.type === "schedule" && dialog.trial
      ? String(dialog.trial.teacherId)
      : String(teachers[0]?.id || ""),
  );
  const [starts, setStarts] = useState(
      dialog.type === "schedule" && dialog.trial ? dialog.trial.startsAt : "",
    ),
    [ends, setEnds] = useState(
      dialog.type === "schedule" && dialog.trial ? dialog.trial.endsAt : "",
    );
  const [result, setResult] = useState("INTERESTED"),
    [notes, setNotes] = useState(""),
    [action, setAction] = useState(""),
    [due, setDue] = useState(""),
    [noFollowup, setNoFollowup] = useState(false),
    [reason, setReason] = useState(""),
    [completion, setCompletion] = useState("");
  const title =
    dialog.type === "student"
      ? "新建学生"
      : dialog.type === "schedule"
        ? dialog.trial
          ? "调整试听"
          : "安排试听"
        : dialog.type === "result"
          ? "记录试听结果"
          : "完成跟进";
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      if (dialog.type === "student") {
        const saved = await api<{ id: number }>("/students", "POST", {
          name,
          contactName,
          phone,
          relationship,
        });
        await onSaved(saved.id);
      } else if (dialog.type === "schedule") {
        const body = {
          studentId: dialog.student.id,
          teacherId: Number(teacher),
          startsAt: starts,
          endsAt: ends,
        };
        if (dialog.trial)
          await api(`/trials/${dialog.trial.id}/schedule`, "PUT", body);
        else await api("/trials", "POST", body);
        await onSaved(dialog.student.id);
      } else if (dialog.type === "result") {
        await api(`/trials/${dialog.trial.id}/complete`, "POST", {
          result,
          notes,
          action: noFollowup ? "" : action,
          dueAt: noFollowup ? "" : due,
          noFollowupReason: noFollowup ? reason : "",
        });
        await onSaved(dialog.student.id);
      } else {
        await api(`/followups/${dialog.followup.id}/complete`, "POST", {
          note: completion,
        });
        await onSaved(dialog.followup.studentId);
      }
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };
  const askAi = async () => {
    if (dialog.type !== "result") return;
    setAiBusy(true);
    setSuggestion(null);
    try {
      setSuggestion(
        await api<Suggestion>("/ai/suggest", "POST", {
          studentId: dialog.student.id,
          notes,
        }),
      );
    } catch (err) {
      setSuggestion({ available: false, message: (err as Error).message });
    } finally {
      setAiBusy(false);
    }
  };
  return (
    <div
      className="modal-overlay"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget && !busy) onClose();
      }}
    >
      <section
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialog-title"
      >
        <div className="modal-head">
          <div>
            <div className="overline">STUDENT DESK</div>
            <h2 id="dialog-title">{title}</h2>
          </div>
          <button
            className="icon-button"
            type="button"
            onClick={onClose}
            aria-label="关闭"
          >
            <X size={20} />
          </button>
        </div>
        <form onSubmit={submit}>
          <div className="modal-body">
            {error && (
              <div className="notice error" role="alert">
                {error}
              </div>
            )}
            {dialog.type === "student" && (
              <>
                <p className="form-intro">先记录学生和一位联系人的基本信息。</p>
                <label>
                  学生姓名
                  <input
                    value={name}
                    maxLength={80}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="请输入学生姓名"
                    required
                  />
                </label>
                <div className="form-row">
                  <label>
                    联系人姓名
                    <input
                      value={contactName}
                      maxLength={80}
                      onChange={(e) => setContactName(e.target.value)}
                      placeholder="家长或学生本人"
                      required
                    />
                  </label>
                  <label>
                    与学生的关系
                    <input
                      value={relationship}
                      maxLength={40}
                      onChange={(e) => setRelationship(e.target.value)}
                      required
                    />
                  </label>
                </div>
                <label>
                  联系电话
                  <input
                    value={phone}
                    maxLength={40}
                    onChange={(e) => setPhone(e.target.value)}
                    placeholder="请输入联系电话"
                    required
                  />
                </label>
              </>
            )}
            {dialog.type === "schedule" && (
              <>
                <p className="form-intro">
                  为 <strong>{dialog.student.name}</strong>{" "}
                  安排试听，时间均为墨尔本当地时间。
                </p>
                <label>
                  授课老师
                  <select
                    value={teacher}
                    onChange={(e) => setTeacher(e.target.value)}
                    required
                  >
                    <option value="">请选择老师</option>
                    {teachers.map((t) => (
                      <option value={t.id} key={t.id}>
                        {t.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  开始时间
                  <input
                    type="datetime-local"
                    value={starts}
                    onChange={(e) => setStarts(e.target.value)}
                    required
                  />
                </label>
                <label>
                  结束时间
                  <input
                    type="datetime-local"
                    value={ends}
                    onChange={(e) => setEnds(e.target.value)}
                    required
                  />
                </label>
                <div className="info-note">
                  <Clock3 size={16} /> 系统会检查学生和老师是否有时间冲突。
                </div>
              </>
            )}
            {dialog.type === "result" && (
              <>
                <p className="form-intro">
                  记录 <strong>{dialog.student.name}</strong>{" "}
                  的试听情况，并确定下一步。
                </p>
                <label>
                  试听结果
                  <select
                    value={result}
                    onChange={(e) => setResult(e.target.value)}
                  >
                    <option value="INTERESTED">有报名意向</option>
                    <option value="UNDECIDED">考虑中</option>
                    <option value="NOT_INTERESTED">暂无意向</option>
                  </select>
                </label>
                <label>
                  试听反馈
                  <textarea
                    value={notes}
                    maxLength={2000}
                    onChange={(e) => setNotes(e.target.value)}
                    placeholder="记录课堂表现或家长反馈"
                    rows={3}
                  />
                </label>
                <button
                  type="button"
                  className="ai-button"
                  disabled={aiBusy || !notes.trim()}
                  onClick={askAi}
                >
                  <Sparkles size={15} />
                  {aiBusy ? "正在整理…" : "AI 辅助整理建议"}
                </button>
                <span className="form-hint">
                  仅发送所填反馈，结果需要你确认。
                </span>
                {suggestion && (
                  <div className="suggestion">
                    {suggestion.available ? (
                      <>
                        <strong>摘要</strong>
                        <p>{suggestion.summary}</p>
                        <strong>建议下一步</strong>
                        <p>{suggestion.suggestedAction}</p>
                        <button
                          type="button"
                          className="text-button"
                          onClick={() => {
                            setNoFollowup(false);
                            setAction(suggestion.suggestedAction || "");
                          }}
                        >
                          使用建议作为跟进计划 <ArrowRight size={14} />
                        </button>
                      </>
                    ) : (
                      suggestion.message
                    )}
                  </div>
                )}
                <label className="check-label">
                  <input
                    type="checkbox"
                    checked={noFollowup}
                    onChange={(e) => setNoFollowup(e.target.checked)}
                  />{" "}
                  无需后续跟进
                </label>
                {noFollowup ? (
                  <label>
                    无需跟进的原因
                    <textarea
                      value={reason}
                      onChange={(e) => setReason(e.target.value)}
                      maxLength={300}
                      rows={2}
                      required
                    />
                  </label>
                ) : (
                  <>
                    <label>
                      下一步跟进计划
                      <input
                        value={action}
                        onChange={(e) => setAction(e.target.value)}
                        maxLength={300}
                        placeholder="例如：联系家长确认报名意向"
                        required
                      />
                    </label>
                    <label>
                      计划联系时间
                      <input
                        type="datetime-local"
                        value={due}
                        onChange={(e) => setDue(e.target.value)}
                        required
                      />
                    </label>
                  </>
                )}
              </>
            )}
            {dialog.type === "followup" && (
              <>
                <p className="form-intro">
                  {dialog.followup.studentName} · {dialog.followup.action}
                </p>
                <label>
                  跟进记录
                  <textarea
                    value={completion}
                    onChange={(e) => setCompletion(e.target.value)}
                    maxLength={1000}
                    placeholder="记录本次联系的结果"
                    rows={4}
                    required
                  />
                </label>
              </>
            )}
          </div>
          <div className="modal-footer">
            <button type="button" className="button ghost" onClick={onClose}>
              取消
            </button>
            <button className="button primary" disabled={busy}>
              {busy ? (
                "正在保存…"
              ) : (
                <>
                  <Check size={16} /> 保存
                </>
              )}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
