// In-memory job list for GET /api/activity.
let seq = 0;

export class Activity {
  constructor(max = 100) {
    this.max = max;
    this.jobs = [];
  }

  /** @param {'scan'|'tag'|'upload'|'plex'} kind */
  add(kind, title, detail = '', state = 'running') {
    const job = {
      id: `j${++seq}`,
      kind,
      title,
      detail,
      state,
      progress: 0,
      updatedAt: Date.now(),
      update: (fields) => { Object.assign(job, fields, { updatedAt: Date.now() }); return job; },
      progressTo: (p, d) => job.update({ progress: Math.max(0, Math.min(1, p)), ...(d != null ? { detail: d } : {}) }),
      done: (d) => job.update({ state: 'done', progress: 1, ...(d != null ? { detail: d } : {}) }),
      fail: (err) => job.update({ state: 'failed', detail: String(err?.message || err || 'failed') }),
      start: (d) => job.update({ state: 'running', ...(d != null ? { detail: d } : {}) }),
    };
    this.jobs.unshift(job);
    if (this.jobs.length > this.max) this.jobs.length = this.max;
    return job;
  }

  list() {
    return this.jobs.map(({ id, kind, title, detail, state, progress }) => ({
      id, kind, title, detail, state, progress: Math.round(progress * 1000) / 1000,
    }));
  }
}
