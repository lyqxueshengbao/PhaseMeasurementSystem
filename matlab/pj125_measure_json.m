function outJson = pj125_measure_json(reqJson)
% pj125_measure_json - minimal contract function for local MATLAB Engine integration.
%
% Input:
%   reqJson: JSON string containing fields:
%     runId, recipeId, mode, repeatIndex, seedKey, mainConfig, relayConfig, linkModel
%
% Output:
%   outJson: JSON string:
%     delayNs, phaseDeg, confidence, snrDb, qualityFlag
%
% NOTE:
% This is a lightweight placeholder implementation so Java can call into MATLAB.
% Replace the internals to use your real chain simulation (e.g. signal4_6G).

req = jsondecode(reqJson);

% model selector (default: placeholder)
model = "placeholder";
if isfield(req, "model") && ~isempty(req.model)
    model = string(req.model);
end
mcT = 2000;
if isfield(req, "monteCarloT") && ~isempty(req.monteCarloT)
    mcT = double(req.monteCarloT);
end

% Map DDS control word -> f0 (carrier / DDS output frequency, Hz)
% If not provided, keep a stable default so existing recipes remain compatible.
f0Hz = 0.8e9;
if isfield(req, "mainConfig") && isfield(req.mainConfig, "ddsFreqHz")
    v = req.mainConfig.ddsFreqHz;
    if ~isempty(v) && isfinite(v) && v > 0
        f0Hz = double(v);
    end
end

seedKey = string(req.seedKey);
ch = double(char(seedKey));
seed = mod(sum(ch .* (1:numel(ch))), 2^31-1);
rng(seed, "twister");

mode = string(req.mode);

% If enabled, use a heavier chain-like Monte Carlo model for LINK.
if model == "signal4_6G_mc" && mode == "LINK"
    [delayNs, delayStdNs, phaseDeg] = pj125_signal4_6G_link_mc(f0Hz, mcT);
    % Convert MC spread to a confidence-like number (heuristic)
    confidence = max(0.0, min(1.0, 0.98 - (delayStdNs / 5.0)));
    if confidence >= 0.85
        qf = "OK";
    elseif confidence >= 0.65
        qf = "WARN";
    elseif confidence >= 0.35
        qf = "BAD";
    else
        qf = "INVALID";
    end

    out = struct();
    out.delayNs = delayNs;
    out.phaseDeg = phaseDeg;
    out.confidence = confidence;
    out.snrDb = 30.0;
    out.qualityFlag = qf;
    out.mcMeanDelayNs = delayNs;
    out.mcStdDelayNs = delayStdNs;
    out.mcT = mcT;
    out.f0Hz = f0Hz;
    outJson = jsonencode(out);
    return;
end

fixed = req.linkModel.fixedLinkDelayNs;
driftPpm = req.linkModel.driftPpm;
noiseStd = max(0, req.linkModel.noiseStdNs);
repeatIndex = double(req.repeatIndex);

if mode == "LINK"
    drift = fixed * (driftPpm / 1e6) * (repeatIndex - 0.5);
    delayNs = fixed + drift + randn() * noiseStd;
elseif mode == "MAIN_INTERNAL"
    delayNs = double(req.mainConfig.measurePathDelayNs) - double(req.mainConfig.referencePathDelayNs);
    delayNs = delayNs + randn() * (noiseStd * 0.5);
else
    delayNs = double(req.relayConfig.measurePathDelayNs) - double(req.relayConfig.referencePathDelayNs);
    delayNs = delayNs + randn() * (noiseStd * 0.5);
end

basePhase = double(req.linkModel.basePhaseDeg);
phaseNoiseDeg = randn() * max(0.1, noiseStd) * 2.0;
phaseSlopeDegPerNs = 360.0 * (f0Hz / 1e9);
phaseDeg = mod(basePhase + phaseSlopeDegPerNs * delayNs + phaseNoiseDeg + 180.0, 360.0) - 180.0;

confidence = max(0.0, min(1.0, 0.98 - min(0.9, noiseStd / 5.0) - abs(phaseNoiseDeg) / 180.0));
snrDb = 30.0 - min(25.0, noiseStd * 10.0) + randn() * 0.5;

if confidence >= 0.85
    qf = "OK";
elseif confidence >= 0.65
    qf = "WARN";
elseif confidence >= 0.35
    qf = "BAD";
else
    qf = "INVALID";
end

out = struct();
out.delayNs = delayNs;
out.phaseDeg = phaseDeg;
out.confidence = confidence;
out.snrDb = snrDb;
out.qualityFlag = qf;

outJson = jsonencode(out);
end
