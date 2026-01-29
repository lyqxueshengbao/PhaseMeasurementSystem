function [delaySec, phaseDiffDeg] = pj125_signal4_6G_link_once(f0Hz)
% pj125_signal4_6G_link_once
% One iteration of a chain-like LINK simulation (no plots/UI).
% This is a best-effort refactor of signal4_6G.m for headless execution.

% Parameters (kept close to the original script)
L = 2^14;
Fs = 16e9;
t = (0:(L-1)) / Fs;

Bit = 16;
snrDb = 30;

Jitter = 50e-15; %#ok<NASGU>
INL = 0.5;
DNL = 1;
noise_rms = 1e-8; %#ok<NASGU>

% LOs
f1 = 1e9;
f2 = 2.3e9;
f3 = 0.2e9;

% Phase noise profile
phase_noise_freq = [1000, 10000, 1e5, 1e6, 1e7];
phase_noise_power = [-90, -110, -130, -150, -160];

% Reference (DDS output)
fai = pi/6;
sig = exp(1i*(2*pi*f0Hz*t + fai));

% --- DAC (simple nonlinearity model)
sig_real = real(sig);
sig_imag = imag(sig);
maxReal = max(sig_real);
maxImag = max(sig_imag);
dac_I = DAC_model(5, sig_real + maxReal, Bit, DNL, INL, Fs, f0Hz - 0.5e6, f0Hz + 0.5e6);
dac_Q = DAC_model(5, sig_imag + maxImag, Bit, DNL, INL, Fs, f0Hz - 0.5e6, f0Hz + 0.5e6);
signal = (dac_I - maxReal) + 1i*(dac_Q - maxImag);

% --- MAIN TX upconversion to ~f1+f0
LO_f1_a = sin(2*pi*f1*t + (2*pi*rand - pi));
signal1 = add_phase_noise(signal .* LO_f1_a, Fs, phase_noise_freq, phase_noise_power);

bp1_end = f1 + f0Hz;
bp1_start = bp1_end - 0.1e9;
signal2 = Filter(signal1, L, Fs, bp1_start, bp1_end);
signal2 = 20 * signal2;

% --- MAIN TX upconversion to ~f2+(f1+f0) and bandpass 4~6 GHz (original)
LO_f2_a = sin(2*pi*f2*t + (2*pi*rand - pi));
signal2 = add_phase_noise(signal2 .* LO_f2_a, Fs, phase_noise_freq, phase_noise_power);
signal2 = Filter(signal2, L, Fs, 4e9, 6e9);
signal2 = 0.1 * signal2;

% --- Air propagation (AWGN)
Am = max(abs(signal2));
% Keep consistent with the original script's usage: normrnd(0, 1/10^(snr/10), ...)
% Here sigma is the time-domain noise standard deviation.
sigma = 1 / (10^(snrDb/10));
signal2 = signal2 / Am + randn(1, L) * sigma;

% --- RELAY RX bandpass
signal3 = Filter(signal2, L, Fs, 4e9, 6.2e9);

% --- RELAY RX downconversion by f2 -> IF around f1+f0
LO_f2_b = sin(2*pi*f2*t + (2*pi*rand - pi));
signal4 = add_phase_noise(signal3 .* LO_f2_b, Fs, phase_noise_freq, phase_noise_power);
bp2_center = f1 + f0Hz;
signal4 = Filter(signal4, L, Fs, bp2_center - 0.1e9, bp2_center + 0.2e9);

% --- RELAY RX downconversion by f1 -> around f0
LO_f1_b = sin(2*pi*f1*t + (2*pi*rand - pi));
signal4 = add_phase_noise(signal4 .* LO_f1_b, Fs, phase_noise_freq, phase_noise_power);
signal5 = Filter(signal4, L, Fs, f0Hz - 0.1e9, f0Hz + 0.2e9);

% --- ADC (vectorized uniform quantization; avoids per-sample SAR loop)
signal5_I = real(signal5);
signal5_Q = imag(signal5);
VrefI = max(1e-9, 10 * max(abs(signal5_I)));
VrefQ = max(1e-9, 10 * max(abs(signal5_Q)));
Do1i = pj125_quantize_uniform(signal5_I, VrefI, Bit);
Do1q = pj125_quantize_uniform(signal5_Q, VrefQ, Bit);
signal5 = Do1i + 1i*Do1q;

% --- Digital upconversion by f3 -> around f0+f3 (~1GHz in original)
LO_f3 = sin(2*pi*f3*t);
signal6 = signal5 .* LO_f3;
center6 = f0Hz + f3;
signal6 = Filter(signal6, L, Fs, center6 - 0.01e9, center6 + 0.01e9);

% --- DAC (again)
sig6_real = real(signal6);
sig6_imag = imag(signal6);
maxReal2 = max(sig6_real);
maxImag2 = max(sig6_imag);
dac2_I = DAC_model(5, sig6_real + maxReal2, Bit, DNL, INL, Fs, center6 - 0.005e9, center6 + 0.005e9);
dac2_Q = DAC_model(5, sig6_imag + maxImag2, Bit, DNL, INL, Fs, center6 - 0.005e9, center6 + 0.005e9);
signal6 = (dac2_I - maxReal2) + 1i*(dac2_Q - maxImag2);

% --- RELAY TX: upconvert by f1 -> around f1+center6 (~2GHz)
signal7 = add_phase_noise(signal6 .* LO_f1_b, Fs, phase_noise_freq, phase_noise_power);
bp_tx1_end = f1 + center6;
bp_tx1_start = bp_tx1_end - 0.1e9;
signal7 = Filter(signal7, L, Fs, bp_tx1_start, bp_tx1_end);
signal7 = 20 * signal7;

% --- RELAY TX: upconvert by f2 -> 4~6 GHz (original)
signal7 = add_phase_noise(signal7 .* LO_f2_b, Fs, phase_noise_freq, phase_noise_power);
signal7 = Filter(signal7, L, Fs, 4.2e9, 6.2e9);

% --- Air propagation back
Am2 = max(abs(signal7));
signal7 = signal7 / Am2 + randn(1, L) * sigma;

% --- MAIN RX bandpass
signal8 = Filter(signal7, L, Fs, 4.0e9, 6.2e9);

% --- MAIN RX downconvert by f2 -> IF
signal9 = add_phase_noise(signal8 .* LO_f2_a, Fs, phase_noise_freq, phase_noise_power);
signal10 = Filter(signal9, L, Fs, bp2_center - 0.1e9, bp2_center + 0.2e9);

% --- MAIN RX downconvert by f1 -> around f0
signal10 = add_phase_noise(signal10 .* LO_f1_a, Fs, phase_noise_freq, phase_noise_power);
signal10 = Filter(signal10, L, Fs, f0Hz - 0.1e9, f0Hz + 0.2e9);

% --- ADC (vectorized uniform quantization)
signal10_I = real(signal10);
signal10_Q = imag(signal10);
Vref2I = max(1e-9, 10 * max(abs(signal10_I)));
Vref2Q = max(1e-9, 10 * max(abs(signal10_Q)));
signal11 = pj125_quantize_uniform(signal10_I, Vref2I, Bit) + 1i * pj125_quantize_uniform(signal10_Q, Vref2Q, Bit);

% --- Digital downconversion by f3 -> around f0 and narrow filter
signal12 = signal11 .* LO_f3;
signal12 = Filter(signal12, L, Fs, f0Hz - 0.5e6, f0Hz + 0.5e6);

% --- Phase measurement via FFT peak (same idea as original)
NN = 15000;
ref = sig(1:NN);
mea = signal12(1:NN);

REF = fft(ref, NN);
MEA = fft(mea, NN);

[~, k_ref] = max(abs(REF));
k_mea = k_ref;

phase_ref = atan2(imag(REF(k_ref)), real(REF(k_ref)));
phase_ref = phase_ref + 2*pi*(phase_ref < 0);

phase_mea = atan2(imag(MEA(k_mea)), real(MEA(k_mea)));
phase_mea = phase_mea + 2*pi*(phase_mea < 0);

phaseDiff = phase_mea - phase_ref;
delaySec = phaseDiff / (2*pi*f0Hz);
phaseDiffDeg = mod(rad2deg(phaseDiff) + 180.0, 360.0) - 180.0;
end

function y = pj125_quantize_uniform(x, Vref, Bit)
% Uniform mid-tread quantizer mapping to [-Vref/2, +Vref/2]
levels = 2^Bit;
xi = x + Vref/2;
xi(xi < 0) = 0;
xi(xi > Vref) = Vref;
q = round(xi / Vref * (levels - 1));
y = q * (Vref / levels) - Vref/2;
end
