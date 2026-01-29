function [meanDelayNs, stdDelayNs, meanPhaseDeg] = pj125_signal4_6G_link_mc(f0Hz, T)
% pj125_signal4_6G_link_mc
% Chain-like LINK Monte Carlo model adapted from signal4_6G.m (no plots/UI).
%
% Inputs:
%   f0Hz: carrier / DDS output frequency in Hz
%   T: Monte Carlo iterations
%
% Outputs:
%   meanDelayNs: mean delay in ns
%   stdDelayNs: std of delay in ns
%   meanPhaseDeg: circular-mean phase difference in deg (wrapped to [-180,180])

if nargin < 2 || isempty(T)
    T = 2000;
end
T = max(1, floor(double(T)));

delays = zeros(1, T);
phases = zeros(1, T);

for k = 1:T
    [delaySec, phaseDeg] = pj125_signal4_6G_link_once(f0Hz);
    delays(k) = delaySec;
    phases(k) = phaseDeg;
end

meanDelayNs = mean(delays) * 1e9;
stdDelayNs = std(delays) * 1e9;

% circular mean for phase in degrees
ang = deg2rad(phases);
mx = mean(cos(ang));
my = mean(sin(ang));
meanPhaseDeg = rad2deg(atan2(my, mx));
if meanPhaseDeg > 180
    meanPhaseDeg = meanPhaseDeg - 360;
elseif meanPhaseDeg < -180
    meanPhaseDeg = meanPhaseDeg + 360;
end
end

