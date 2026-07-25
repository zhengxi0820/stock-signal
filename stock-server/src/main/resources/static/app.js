function __APP_MAIN__(createApp) {
createApp({
    data() {
        return {
            today: new Date().toISOString().slice(0, 10),
            todaySignals: [],
            currentSignals: [],
            histMarket: 'SH', histCode: '', historySignals: [],
            chartStock: null,
            poolName: 'default', poolMarket: 'SH', poolCode: '', poolEntries: [],
            needLogin: false, password: '', loginError: '',
            runStatus: []
        };
    },
    computed: {
        // 按股票聚合：每只股票一行，策略以标记叠加
        goldenStocks() {
            const byStock = {};
            for (const s of this.currentSignals) {
                const key = s.market + ':' + s.code;
                if (!byStock[key]) {
                    byStock[key] = { key, market: s.market, code: s.code, signals: [],
                        since: s.tradeDate, indicator: s.indicator, eastmoneyUrl: s.eastmoneyUrl };
                }
                byStock[key].signals.push(s);
                if (s.tradeDate < byStock[key].since) byStock[key].since = s.tradeDate;
            }
            return Object.values(byStock)
                .map(g => ({ ...g, days: Math.max(0, Math.round((new Date(this.today) - new Date(g.since)) / 86400000)) }))
                .sort((a, b) => b.since.localeCompare(a.since));
        },
        deathCount() {
            return this.deathSignals.length;
        },
        deathSignals() {
            return this._death || [];
        }
    },
    mounted() {
        this.loadAll();
        // 支持 #chart=SH:600519 直接打开指标图（便于分享链接与自动化验证）
        const m = location.hash.match(/chart=(SH|SZ|HK|US):(\w+)/);
        if (m) this.showChart(m[1], m[2]);
    },
    methods: {
        // fetch 包装：401 时弹出登录浮层
        async api(url, options) {
            const r = await fetch(url, options);
            if (r.status === 401) {
                this.needLogin = true;
                throw new Error('unauthorized');
            }
            return r.json();
        },
        async login() {
            const r = await fetch('/api/auth/login', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ password: this.password })
            });
            if (r.ok) {
                this.needLogin = false;
                this.loginError = '';
                this.password = '';
                this.loadAll();
            } else if (r.status === 429) {
                this.loginError = '失败次数过多，已锁定 15 分钟';
            } else {
                this.loginError = '口令错误，请重试';
            }
        },
        async loadAll() {
            try {
                const [today, golden, death, pool, runs] = await Promise.all([
                    this.api('/api/signals'),
                    this.api('/api/signals/current?state=GOLDEN_CROSS'),
                    this.api('/api/signals/current?state=DEATH_CROSS'),
                    this.api(`/api/pools/${this.poolName}/entries`),
                    this.api('/api/runs/latest')
                ]);
                this.todaySignals = today;
                this.currentSignals = golden;
                this._death = death;
                this.poolEntries = pool;
                this.runStatus = runs;
            } catch (e) { /* 401 已弹登录框 */ }
        },
        async queryHistory() {
            if (!this.histCode) return;
            this.historySignals = await this.api(`/api/signals/stock?market=${this.histMarket}&code=${this.histCode}`);
            this.showChart(this.histMarket, this.histCode);
        },
        async showChart(market, code) {
            this.chartStock = `${market}:${code}`;
            const data = await this.api(`/api/stocks/${market}/${code}/indicators/kdj`);
            this.$nextTick(() => {
                this.renderChart(data);
                document.getElementById('chart').scrollIntoView({ behavior: 'smooth', block: 'center' });
            });
        },
        renderChart(data) {
            const el = document.getElementById('chart');
            // 图表实例不能放进 Vue data：Vue3 会把它包成响应式 Proxy，
            // 导致 ECharts 内部状态错乱（典型症状：图能渲染但悬浮提示/交互失效）
            if (this._chart) this._chart.dispose();
            this._chart = echarts.init(el, null, { renderer: 'canvas' });
            const dates = data.map(p => p.date);
            const closes = data.map(p => p.close);
            const ks = data.map(p => p.k);
            const ds = data.map(p => p.d);
            const js = data.map(p => p.j);
            this._chart.setOption({
                backgroundColor: 'transparent',
                tooltip: {
                    trigger: 'axis',
                    confine: true,
                    axisPointer: {
                        type: 'line',
                        snap: true,
                        link: [{ xAxisIndex: 'all' }],
                        lineStyle: { color: '#b8c2d4' }
                    },
                    backgroundColor: '#ffffff', borderColor: '#e3e8f0',
                    textStyle: { color: '#2b3445', fontSize: 12 },
                    // 直接按日期索引取数，不依赖悬浮在哪个图上：两个图都显示 日期+收盘价+K/D/J
                    formatter: params => {
                        if (!params || !params.length) return '';
                        const i = params[0].dataIndex;
                        const fmt = v => v == null ? '-' : Number(v).toFixed(2);
                        return `<b>${dates[i]}</b><br/>收盘价: ${fmt(closes[i])}`
                            + `<br/>K: ${fmt(ks[i])}&nbsp;&nbsp;D: ${fmt(ds[i])}&nbsp;&nbsp;J: ${fmt(js[i])}`;
                    }
                },
                legend: { data: ['K', 'D', 'J'], textStyle: { color: '#8a94a8' }, top: 0, right: 0 },
                grid: [{ left: 50, right: 16, top: 30, height: '44%' }, { left: 50, right: 16, top: '64%', height: '26%' }],
                xAxis: [
                    { type: 'category', data: dates, gridIndex: 0, axisLabel: { color: '#8a94a8' }, axisLine: { lineStyle: { color: '#d8dee9' } } },
                    { type: 'category', data: dates, gridIndex: 1, axisLabel: { color: '#8a94a8' }, axisLine: { lineStyle: { color: '#d8dee9' } } }
                ],
                yAxis: [
                    { gridIndex: 0, scale: true, axisLabel: { color: '#8a94a8' }, splitLine: { lineStyle: { color: '#eef1f6' } } },
                    { gridIndex: 1, axisLabel: { color: '#8a94a8' }, splitLine: { lineStyle: { color: '#eef1f6' } } }
                ],
                series: [
                    { name: '收盘价', type: 'line', data: closes, xAxisIndex: 0, yAxisIndex: 0, showSymbol: false,
                      lineStyle: { color: '#2b3445', width: 1.5 }, itemStyle: { color: '#2b3445' } },
                    { name: 'K', type: 'line', data: ks, xAxisIndex: 1, yAxisIndex: 1, showSymbol: false, lineStyle: { width: 1.5 } },
                    { name: 'D', type: 'line', data: ds, xAxisIndex: 1, yAxisIndex: 1, showSymbol: false, lineStyle: { width: 1.5 } },
                    { name: 'J', type: 'line', data: js, xAxisIndex: 1, yAxisIndex: 1, showSymbol: false, lineStyle: { width: 1 } }
                ]
            });
            this._chart.resize();
            // 验证钩子：#chart=...&tip=N 时自动弹出第 N 天的悬浮框（用于自动化截图验证）
            const tip = location.hash.match(/tip=(\d+)/);
            if (tip) {
                const idx = Math.min(parseInt(tip[1]), data.length - 1);
                this._chart.dispatchAction({ type: 'showTip', seriesIndex: 0, dataIndex: idx });
            }
        },
        eastmoneyUrl(market, code) {
            if (market === 'SH' || market === 'SZ') return `https://quote.eastmoney.com/${market.toLowerCase()}${code}.html`;
            if (market === 'HK') return `https://quote.eastmoney.com/hk/${code}.html`;
            return `https://quote.eastmoney.com/us/${code}.html`;
        },
        async addToPool() {
            if (!this.poolCode) return;
            await this.api(`/api/pools/${this.poolName}/entries?market=${this.poolMarket}&code=${this.poolCode}`, { method: 'POST' });
            this.poolCode = '';
            this.loadAll();
        },
        async removeFromPool(e) {
            await this.api(`/api/pools/${this.poolName}/entries?market=${e.market}&code=${e.code}`, { method: 'DELETE' });
            this.loadAll();
        }
    }
}).mount('#app');
}

// vendor 本地脚本（defer 顺序执行）已就绪，直接启动
__APP_MAIN__(Vue.createApp);
