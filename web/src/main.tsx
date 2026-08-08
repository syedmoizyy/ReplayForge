import React, {useEffect, useMemo, useState} from 'react';
import {createRoot} from 'react-dom/client';
import './styles.css';
import './states.css';
import {replay, trace} from './data';

type Screen = 'traces' | 'runner' | 'replay' | 'report';
type LoadState = 'loading' | 'ready' | 'error';
const screens: {id: Screen; label: string}[] = [
  {id: 'traces', label: 'Traces'}, {id: 'runner', label: 'Scenario runner'},
  {id: 'replay', label: 'Replay detail'}, {id: 'report', label: 'Divergence report'}
];

function Metric({label, value}: {label: string; value: string}) {
  return <div className="metric"><span>{label}</span><strong>{value}</strong></div>;
}

function Timeline({fault = false}: {fault?: boolean}) {
  return <ol className="timeline" aria-label="Event timeline">{trace.events.map((event, index) =>
    <li key={`${event[1]}-${index}`}><time>{event[0]}</time><i className={fault && index === 1 ? 'fault' : ''}/>
      <div><b>{event[1]}</b><small>seq {index + 1} · {event[2]}</small>
        {fault && index === 1 && <em>injected duplicate</em>}</div></li>)}</ol>;
}

function Traces({go}: {go: (screen: Screen) => void}) {
  const [query, setQuery] = useState('');
  const forcedError = new URLSearchParams(location.search).get('state') === 'error';
  const [loadState, setLoadState] = useState<LoadState>(forcedError ? 'error' : 'loading');
  useEffect(() => { if (forcedError) return; const timer = setTimeout(() => setLoadState('ready'), 250); return () => clearTimeout(timer); }, [forcedError]);
  const matches = useMemo(() => !query || trace.correlationId.includes(query) || trace.events.some(e => e[1].toLowerCase().includes(query.toLowerCase())), [query]);
  return <><header><div><p>CAPTURED WORKFLOWS</p><h1>Event traces</h1></div><span className="live">● generated fixture</span></header>
    <section className="panel"><div className="toolbar"><label htmlFor="trace-filter">Filter traces
      <input id="trace-filter" value={query} onChange={e => setQuery(e.target.value)} placeholder="Correlation ID or event type"/></label>
      <button onClick={() => go('runner')}>Run scenario</button></div>
      {loadState === 'loading' ? <p role="status" className="state">Loading captured traces…</p> :
        loadState === 'error' ? <div role="alert" className="state error">Trace fixtures could not be loaded. <button onClick={() => setLoadState('ready')}>Retry</button></div> :
        matches ? <button className="traceRow" onClick={() => go('replay')}><div><b>Reservation payout</b><code>{trace.correlationId}</code></div>
          <Metric label="Events" value="5"/><Metric label="Duration" value={trace.duration}/><Metric label="Throughput" value={trace.throughput}/><span className="ok">complete →</span></button> :
        <p className="state">No traces match “{query}”. Clear the filter to inspect the seeded trace.</p>}
    </section>
    <section className="split"><div className="panel"><h2>Selected trace</h2><Timeline/></div><div className="panel dense"><h2>Final state</h2>
      <pre>{JSON.stringify({reservationStatus: 'CONFIRMED', depositAuthorized: true, refundStatus: 'NONE', payoutStatus: 'SENT'}, null, 2)}</pre></div></section></>;
}

function Runner({go}: {go: (screen: Screen) => void}) {
  const [running, setRunning] = useState(false);
  useEffect(() => { if (!running) return; const timer = setTimeout(() => { setRunning(false); go('replay'); }, 900); return () => clearTimeout(timer); }, [running, go]);
  return <><header><div><p>CONTROLLED EXPERIMENT</p><h1>Scenario runner</h1></div></header><section className="split">
    <div className="panel form"><label>Source correlation ID<input value={trace.correlationId} readOnly/></label>
      <label>Fault scenario<select defaultValue="duplicate"><option value="duplicate">Duplicate payment authorization</option><option>Drop refund request</option><option>Dependency timeout + retries</option></select></label>
      <div className="grid"><label>Seed<input type="number" defaultValue="101"/></label><label>Checkpoint<input type="number" min="0" defaultValue="0"/></label></div>
      <button disabled={running} onClick={() => setRunning(true)}>{running ? 'Compiling schedule…' : 'Start deterministic replay'}</button>
      {running && <div role="status" className="progress"><b>Replay running</b><span>Compiling a bounded schedule in an isolated namespace. It is safe to leave this screen open.</span><progress/></div>}
    </div><div className="panel"><h2>Compiled intent</h2><dl><dt>Selector</dt><dd>DepositAuthorized</dd><dt>Mutation</dt><dd>Duplicate ×1</dd><dt>Execution</dt><dd>Fixed epoch · isolated</dd><dt>Limits</dt><dd>100 source events · 3 duplicates</dd></dl></div></section></>;
}

function Replay({go}: {go: (screen: Screen) => void}) {
  return <><header><div><p>REPLAY / {replay.id}</p><h1>Replay detail</h1></div><span className="ok">● {replay.status}</span></header>
    <p className="correlation"><span>Correlation ID</span><code>{trace.correlationId}</code></p>
    <div className="metrics"><Metric label="Duration" value={replay.duration}/><Metric label="Throughput" value={replay.throughput}/><Metric label="Injected faults" value="1"/><Metric label="Violations" value="1 hard"/></div>
    <section className="split"><div className="panel"><h2>Replay timeline</h2><Timeline fault/></div><div className="panel"><h2>Execution evidence</h2><p className="faultbox">{replay.fault}</p>
      <dl><dt>Seed</dt><dd>101</dd><dt>Checkpoint</dt><dd>0</dd><dt>Clock</dt><dd>FIXED_EPOCH</dd></dl><button onClick={() => go('report')}>Open divergence report →</button></div></section></>;
}

function Report() {
  const exportReport = () => {
    const body = JSON.stringify({fixture: true, correlationId: trace.correlationId, replay}, null, 2);
    const link = document.createElement('a'); link.href = URL.createObjectURL(new Blob([body], {type: 'application/json'})); link.download = `${replay.id}-report.json`; link.click(); URL.revokeObjectURL(link.href);
  };
  return <><header><div><p>CAUSAL COMPARISON</p><h1>Divergence report</h1></div><button onClick={exportReport}>Export JSON</button></header>
    <section className="alert"><b>First divergence at event 3</b><span>Duplicate authorization changed financial-effect cardinality before payout.</span></section>
    <section className="split"><div className="panel"><h2>Invariant violations</h2>{replay.violations.map(v => <article className="violation" key={v.rule}><span>{v.severity}</span><b>{v.rule}</b><p>Position {v.position} · {v.actual}</p></article>)}</div>
      <div className="panel"><h2>Final-state diff</h2><table><thead><tr><th>Field</th><th>Baseline</th><th>Replay</th></tr></thead><tbody>{replay.diff.map(d => <tr key={d[0]}><th>{d[0]}</th><td>{d[1]}</td><td className="changed">{d[2]}</td></tr>)}</tbody></table></div></section></>;
}

function App() {
  const initial = location.hash.slice(1) as Screen;
  const [screen, setScreen] = useState<Screen>(screens.some(s => s.id === initial) ? initial : 'traces');
  const go = React.useCallback((next: Screen) => { location.hash = next; setScreen(next); }, []);
  return <div className="shell"><aside><a className="brand" href="#traces" onClick={() => go('traces')}>RF<span>ReplayForge</span></a>
    <nav aria-label="Primary">{screens.map(item => <button key={item.id} aria-current={screen === item.id ? 'page' : undefined} onClick={() => go(item.id)}>{item.label}</button>)}</nav>
    <small>Local development<br/>API v1</small></aside><main>{screen === 'traces' ? <Traces go={go}/> : screen === 'runner' ? <Runner go={go}/> : screen === 'replay' ? <Replay go={go}/> : <Report/>}</main></div>;
}

createRoot(document.getElementById('root')!).render(<App/>);
