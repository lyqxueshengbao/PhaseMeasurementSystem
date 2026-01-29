%% 4-6GHz系统
clc
clear all
close all
%%%  产生中心频率0.8GHz ， 采样率12GHz的单频信号
L    =   2^14;                                          % 信号点数    
B     = 200e6;                                          % 发射带宽

Fs    = 16e9;                                           % 发射信号采样率   
Tp    = L/Fs;                                           % 发射时宽. 控制点数.比如要输出的点数是L， Tp = L /Fs.

f0    = 0.8e9;                                          % 中心频率
internal = 1/Fs;
t     = 0:1/Fs:(L-1)/Fs;
% 发射采样时刻
fai = pi/6;   % pi/3
sig   =  exp(1i*(2*pi*f0*t+fai)) ;
% sig   = sin(2*pi*f0*t+pi/4); 
Bit   = 16;
Vreff = 1;
snr = 30;

Jitter = 50e-15;                                       % 时钟抖动
INL = 0.5;
DNL =1;
noise_rms = 1e-8;

% 本振
f1 = 1e9;                 % 一级本振频率
f2 = 2.3e9;                 % 二级本振频率
f3 = 0.2e9;                 % 数字变频（本振）频率
phase_noise_freq = [1000, 10000,1e5,1e6,1e7];            % 单边带(SSB)相位噪声定义的频率，即相对于载波的偏移量(Hz)。
phase_noise_power = [-90,-110,-130,-150,-160];           % 单边带相位噪声功率(dBc/Hz)

T = 2000;
hh = 0;
record = zeros(1,T);
h = waitbar(0,'1','Name','Simulation');
for pp = 1:T
tic
% figure;
% real_s_tmp = real (sig);
% real_s = floor (L * real_s_tmp);
% plot(linspace(-Fs/2,Fs/2,L),db(fftshift(fft(real_s))));
% title('理想单频信号频谱');


sig_real = real(sig);
sig_image = imag(sig);
% [~, dac_I] = adv_dac_model(sig_real, Bit, 1, 1e9,...
%     'INL', INL, ...        %  LSB的INL误差
%     'DNL', DNL, ...      %  LSB的DNL误差  
%     'jitter', Jitter, ...% 时钟抖动
%     'noise_rms', noise_rms);  % 热噪声
% 
% [~, dac_Q] = adv_dac_model(sig_image, Bit, 1, 1e9,...
%     'INL', INL, ...        % LSB的INL误差
%     'DNL', DNL, ...      % LSB的DNL误差  
%     'jitter', Jitter, ...% 时钟抖动
%     'noise_rms', noise_rms);  % 热噪声
maxReal = max(sig_real);
maxImage = max(sig_image);
% 
dac_I= DAC_model(5,sig_real+maxReal,Bit,DNL,INL,Fs,0.7995e9,0.8005e9) ;
dac_Q = DAC_model(5,sig_image+maxImage,Bit,DNL,INL,Fs,0.7995e9,0.8005e9) ;
dac_I = dac_I -maxReal;
dac_Q = dac_Q - maxImage;
signal = dac_I+1i*dac_Q; 

% figure;
% real_sig_tmp = real (signal);
% real_sig = floor (L * real_sig_tmp);
% plot(linspace(-Fs/2,Fs/2,length(signal)),db(fftshift(fft(real_sig))));
% title('DDS输出频谱(未滤波)');

 
% figure;
% real_sig_tmp = real (signal);
% real_sig = floor (L * real_sig_tmp);
% plot(linspace(-Fs/2,Fs/2,length(signal)),db(fftshift(fft(real_sig))));
% xlabel('频率');
% ylabel('幅度');
% title('DDS输出频谱(经过滤波后)');

% 
% figure;
% subplot(211)
% plot(real(signal(1:1200)));
% title('最终比相信号时域波形');
% subplot(212)
% plot(real(sig(1:1200)));
% title('初始参考信号时域波形');
% 
% figure;
% subplot(211)
% plot(imag(signal(1:1200)));
% title('最终比相信号时域波形');
% subplot(212)
% plot(imag(sig(1:1200)));
% title('初始参考信号时域波形');


% 
% signal = sig;       %删除DAC

%% 主站发射路
% 主站一级上变频

F1 = sin(2*pi*f1*t+(2*pi*rand-pi));
signal1 = signal.*F1;
signal1 = add_phase_noise(signal1, Fs, phase_noise_freq, phase_noise_power); 

% figure;
% real_sig_tmp1 = real (signal1);
% real_sig1 = floor (L * real_sig_tmp1);
% plot(linspace(-Fs/2,Fs/2,length(signal)),db(fftshift(fft(real_sig1))));
% title('主站发射路混频器输出频谱');

% 滤波器
% 1.7GHz到1.8GHz带通滤波器(整个频段的)
start1 = 1.7e9;                                                         % 滤波器起始频率                            
end1 = 1.8e9;                                                           % 滤波器截止频率
signal2 = Filter(signal1,L,Fs,start1,end1);

% figure;
% real_sig_tmp2 = real (signal2);
% real_sig2 = floor (L * real_sig_tmp2);
% plot(linspace(-Fs/2,Fs/2,length(signal)),db(fftshift(fft(real_sig2))));
% xlabel('频率');
% ylabel('幅度');
% title('主站发射路一次变频且滤波的输出频谱');

% 功率放大器+驱动放大器1
signal2 = 20*signal2;

%
F2 = sin(2*pi*f2*t+(2*pi*rand-pi));
signal2 = signal2.*F2;
signal2 = add_phase_noise(signal2, Fs, phase_noise_freq, phase_noise_power); 

start2 = 4e9;                                                         % 滤波器起始频率                            
end2 = 6e9;                                                           % 滤波器截止频率
signal2 = Filter(signal2,L,Fs,start2,end2);

% figure;
% real_sig_tmp2 = real (signal2);
% real_sig2 = floor (L * real_sig_tmp2);
% plot(linspace(-Fs/2,Fs/2,length(signal)),db(fftshift(fft(real_sig2))));
% xlabel('频率');
% ylabel('幅度');
% title('主站发射路二次变频且滤波的输出频谱');

% 功率放大器+驱动放大器2
signal2 = 0.1*signal2;
%% 在空气中传播
Am = max(abs(signal2));

signal2 =  signal2/Am + normrnd(0,1/10^(snr/10),[1,length(t)]);
%% 转发站接收路
% 低噪放
signal2 = 0.0001*signal2;

% 滤除频带外的干扰
start2 = 4e9;                                           % 滤波器起始频率
end2 = 6.2e9;                                           % 滤波器截止频率
signal3 = Filter(signal2,L,Fs,start2,end2);

% figure;
% real_sig_tmp3 = real (signal3);
% real_sig3 = floor (L * real_sig_tmp3);
% plot(linspace(-Fs/2,Fs/2,L),db(fftshift(fft(real_sig3))));
% xlabel('频率');
% ylabel('幅度');
% title('转发站接收路滤波器输出频谱');


% 一次下变频

F3 = sin(2*pi*f2*t+(2*pi*rand-pi));
signal4 = signal3.*F3;
signal4 = add_phase_noise(signal4, Fs, phase_noise_freq, phase_noise_power); 

% figure;
% real_sig_tmp4 = real (signal4);
% real_sig4 = floor (L * real_sig_tmp4);
% plot(linspace(-Fs/2,Fs/2,L),db(fftshift(fft(real_sig4))));
% title('下变频输出频谱');

%滤波器
start3 = 1.7e9;                                         % 滤波器起始频率
end3 = 2.0e9;                                           % 滤波器截止频率
signal4 = Filter(signal4,L,Fs,start3,end3);

% figure;
% real_sig_tmp5 = real (signal4);
% real_sig5 = floor (L * real_sig_tmp5);
% plot(linspace(-Fs/2,Fs/2,L),db(fftshift(fft(real_sig5))));
% xlabel('频率');
% ylabel('幅度');
% title('转发站接收路一次下变频且滤波的输出频谱');

% 二次下变频
F4 = sin(2*pi*f1*t+(2*pi*rand-pi));
signal4 = signal4.*F4;
signal4 = add_phase_noise(signal4, Fs, phase_noise_freq, phase_noise_power); 
% 滤波器
start4 = 0.7e9;                                         % 滤波器起始频率
end4 = 1.0e9;                                           % 滤波器截止频率
signal5 = Filter(signal4,L,Fs,start4,end4);

% figure;
% real_sig_tmp5 = real (signal5);
% real_sig5 = floor (L * real_sig_tmp5);
% plot(linspace(-Fs/2,Fs/2,L),db(fftshift(fft(real_sig5))));
% xlabel('频率');
% ylabel('幅度');
% title('转发站接收路二次下变频且滤波的输出频谱');



% ADC采样
% 需要将其分为I路和Q路进行采样
signal5_I = real(signal5);
signal5_Q = imag(signal5);

Vref11 = 10*max(abs(signal5_I));                            % 根据当前信号幅度值确定的（需要调整才能保持一致）
Vref12 = 10*max(abs(signal5_Q));
% Vref11 = 0.085;
% Vref12 = 0.2;

Di1i = zeros(1,L);
Do1i = zeros(1,L);                                       % i路量化输出
Di1q = zeros(1,L);
Do1q = zeros(1,L);                                       % q路量化输出
for idx = 1:L    
    Di1i(idx) = sar_c(Vref11,signal5_I(idx)+Vref11/2,Bit);   % i路      
    Do1i(idx) = Di1i(idx)*Vref11/(2^Bit)-Vref11/2;
    Di1q(idx) = sar_c(Vref12,signal5_Q(idx)+Vref12/2,Bit);   % q路      
    Do1q(idx) = Di1q(idx)*Vref12/(2^Bit)-Vref12/2;
end
% figure;
% plot(1:100,signal5_I(1:100),'r*');
% hold on;
% plot(1:100,Do1i(1:100),'b');
% title('前100个数据为例，比较模拟信号与采样信号后的差异');


ENOB1i=adc_analysis(Bit,signal5_I,Do1i,Di1i);            % i路数据的ADC采样结果性能分析
ENOB1q=adc_analysis(Bit,signal5_Q,Do1q,Di1q);            % q路数据的ADC采样结果性能分析

signal5 = Do1i+1i*Do1q;

% signal5_test = Filter(signal5,L,Fs,0.7999e9,0.8001e9);
% figure;
% subplot(211)
% plot(real(signal5_test(1:1000)));
% title('最终比相信号时域波形');
% subplot(212)
% plot(real(sig(1:1000)));
% title('初始参考信号时域波形');


% figure;
% real_sig_tmp5 = real (signal5);
% real_sig5 = floor (L * real_sig_tmp5);
% plot(linspace(-Fs/2,Fs/2,length(signal)),db(fftshift(fft(real_sig5))));
% xlabel('频率');
% ylabel('幅度');
% title('ADC输出频谱');



%% 数字变频
% 上变频+数字滤波
F5 = sin(2*pi*f3*t);                                    % 数字上变频本振                                                        
signal6 = signal5.*F5;

start4 = 0.99e9;                                          % 滤波器起始频率                            
end4 = 1.01e9;                                            % 滤波器截止频率
signal6 = Filter(signal6,L,Fs,start4,end4);

% figure;
% real_sig_tmp6 = real (signal6);
% real_sig6 = floor (L * real_sig_tmp6);
% plot(linspace(-Fs/2,Fs/2,length(signal)),db(fftshift(fft(real_sig6))));
% xlabel('频率');
% ylabel('幅度');
% title('混频器输出频谱');

% DAC
sig6_real = real(signal6);
sig6_image = imag(signal6);
% % [~, dac2_I] = adv_dac_model(sig6_real, Bit, 1, 1e9,...
% %     'INL', INL, ...        % LSB的INL误差
% %     'DNL', DNL, ...      % LSB的DNL误差  
% %     'jitter', Jitter, ...% 时钟抖动
% %     'noise_rms', noise_rms);  % 热噪声
% % 
% % [~, dac2_Q] = adv_dac_model(sig6_image, Bit, 1, 1e9,...
% %     'INL', INL, ...        % LSB的INL误差
% %     'DNL', DNL, ...      % LSB的DNL误差  
% %     'jitter', Jitter, ...% 时钟抖动
% %     'noise_rms', noise_rms);  % 热噪声
maxReal2 = max(sig6_real);
maxImage2 = max(sig6_image);

dac2_I= DAC_model(5,sig6_real+maxReal2,Bit,DNL,INL,Fs,0.995e9,1.005e9) ;
dac2_Q = DAC_model(5,sig6_image+maxImage2,Bit,DNL,INL,Fs,0.995e9,1.005e9) ;
dac2_I = dac2_I -maxReal2;
dac2_Q = dac2_Q - maxImage2;
% 
signal6 = dac2_I+1i*dac2_Q; 
% 
% figure;
% subplot(211)
% plot(real(signal6(1:1200)));
% title('最终比相信号时域波形');
% subplot(212)
% plot(real(sig(1:1200)));
% title('初始参考信号时域波形');
% 
% figure;
% real_sig6_tmp = real (signal6);
% real_sig6 = floor (L * real_sig6_tmp);
% plot(linspace(-Fs/2,Fs/2,length(signal)),db(fftshift(fft(real_sig6))));
% xlabel('频率');
% ylabel('幅度');
% title('数字变频DAC输出信号频谱');




%% 转发站发送路
% 一次上变频+滤波
signal7 = signal6.*F4;                                   % 本振同源，直接乘
signal7 = add_phase_noise(signal7, Fs, phase_noise_freq, phase_noise_power); 

% figure;
% real_sig_tmp7 = real (signal7);
% real_sig7 = floor (L * real_sig_tmp7);
% plot(linspace(-Fs/2,Fs/2,L),db(fftshift(fft(real_sig7))));
% title('转发站发射路上变频信号频谱');

start5 = 1.9e9;                                          % 滤波器起始频率
end5 = 2.0e9;                                            % 滤波器截止频率
signal7 = Filter(signal7,L,Fs,start5,end5);

% figure;
% real_sig_tmp7 = real (signal7);
% real_sig7 = floor (L * real_sig_tmp7);
% plot(linspace(-Fs/2,Fs/2,L),db(fftshift(fft(real_sig7))));
% xlabel('频率');
% ylabel('幅度');
% title('转发站发射路一次上变频信号频谱');

% 驱放+功放
signal7 = 20*signal7;

% 二次上变频+滤波
signal7 = signal7.*F3;                                   % 本振同源，直接乘
signal7 = add_phase_noise(signal7, Fs, phase_noise_freq, phase_noise_power); 
start5 = 4.2e9;                                          % 滤波器起始频率
end5 = 6.2e9;                                            % 滤波器截止频率
signal7 = Filter(signal7,L,Fs,start5,end5);

% figure;
% real_sig_tmp7 = real (signal7);
% real_sig7 = floor (L * real_sig_tmp7);
% plot(linspace(-Fs/2,Fs/2,L),db(fftshift(fft(real_sig7))));
% xlabel('频率');
% ylabel('幅度');
% title('转发站发射路二次上变频信号频谱');

%% 在空气中传播
Am2 = max(abs(signal7));

signal7 =  signal7/Am2 + normrnd(0,1/10^(snr/10),[1,length(t)]);
%% 主站接收路
% 低噪放
signal7 = Am2*signal7;

% 滤除频带外的干扰
start6 = 4.0e9;                                          % 滤波器起始频率
end6 = 6.2e9;                                            % 滤波器截止频率
signal8 = Filter(signal7,L,Fs,start6,end6);

% figure;
% real_sig_tmp8 = real (signal8);
% real_sig8 = floor (L * real_sig_tmp8);
% plot(linspace(-Fs/2,Fs/2,L),db(fftshift(fft(real_sig8))));
% xlabel('频率');
% ylabel('幅度');
% title('主站接收信号并滤波后的频谱');

% 一次下变频
signal9 = signal8.*F2;                                   % 本振同源，直接乘
signal9 = add_phase_noise(signal9, Fs, phase_noise_freq, phase_noise_power); 
start7 = 1.7e9;                                          % 滤波器起始频率
end7 = 2.0e9;                                            % 滤波器截止频率
signal10 = Filter(signal9,L,Fs,start7,end7);

% figure;
% real_sig_tmp10 = real (signal10);
% real_sig10 = floor (L * real_sig_tmp10);
% plot(linspace(-Fs/2,Fs/2,L),db(fftshift(fft(real_sig10))));
% xlabel('频率');
% ylabel('幅度');
% title('主站一次下变频且滤波后的信号频谱');

% 二次下变频
signal10 = signal10.*F1;                                   % 本振同源，直接乘
signal10 = add_phase_noise(signal10, Fs, phase_noise_freq, phase_noise_power); 
start7 = 0.7e9;                                          % 滤波器起始频率
end7 = 1.0e9;                                            % 滤波器截止频率
signal10 = Filter(signal10,L,Fs,start7,end7);



% figure;
% real_sig_tmp10 = real (signal10);
% real_sig10 = floor (L * real_sig_tmp10);
% plot(linspace(-Fs/2,Fs/2,L),db(fftshift(fft(real_sig10))));
% xlabel('频率');
% ylabel('幅度');
% title('主站二次下变频且滤波后的信号频谱');

% ADC采样
signal10_I = real(signal10);
signal10_Q = imag(signal10);

Vref21 = 10*max(abs(signal10_I));
Vref22 = 10*max(abs(signal10_Q));

Di2i = zeros(1,L);
Do2i = zeros(1,L);                                       % i路量化输出
Di2q = zeros(1,L);
Do2q = zeros(1,L);                                       % q路量化输出
for idx = 1:L    
    Di2i(idx) = sar_c(Vref21,signal10_I(idx)+Vref21/2,Bit);   % i路      
    Do2i(idx) = Di2i(idx)*Vref21/(2^Bit)-Vref21/2;
    Di2q(idx) = sar_c(Vref22,signal10_Q(idx)+Vref22/2,Bit);   % q路      
    Do2q(idx) = Di2q(idx)*Vref22/(2^Bit)-Vref22/2;
end

% figure;
% plot(1:100,signal10_I(1:100),'r*');
% hold on;
% plot(1:100,Do2i(1:100),'b');
% title('前100个数据为例，比较模拟信号与采样信号后的差异');

ENOB2i=adc_analysis(Bit,signal10_I,Do2i,Di2i);            % i路数据的ADC采样结果性能分析
ENOB2q=adc_analysis(Bit,signal10_Q,Do2q,Di2q);            % q路数据的ADC采样结果性能分析

signal11 = Do2i+1i*Do2q;

% figure;
% real_sig_tmp11 = real (signal11);
% real_sig11 = floor (L * real_sig_tmp11);
% plot(linspace(-Fs/2,Fs/2,length(signal11)),db(fftshift(fft(real_sig11))));
% xlabel('频率');
% ylabel('幅度');
% title('ADC输出频谱');

%signal11 = signal10;                    % 删除ADC

% 数字变频
% 下变频+数字滤波
F6 = sin(2*pi*f3*t);                                    % 数字上变频本振                                                        
signal12 = signal11.*F6;
start5 = 0.7995e9;                                            % 滤波器起始频率                            
end5 = 0.8005e9;                                            % 滤波器截止频率
signal12 = Filter(signal12,L,Fs,start5,end5);

% figure;
% real_sig_tmp12 = real (signal12);
% real_sig12 = floor (L * real_sig_tmp12);
% plot(linspace(-Fs/2,Fs/2,L),db(fftshift(fft(real_sig12))));
% xlabel('频率');
% ylabel('幅度');
% title('数字下变频且滤波后信号的频谱');
% 
figure;
subplot(211)
plot(linspace(-Fs/2,Fs/2,L),fftshift((fft(real(signal12)))));
title('最终比相信号的频谱');
subplot(212)
plot(linspace(-Fs/2,Fs/2,L),fftshift((fft(real(sig)))));
title('初始参考信号的频谱');
% 
% figure;
subplot(211)
plot(real(signal12(1:2000)));
title('最终比相信号时域波形');
subplot(212)
plot(real(sig(1:2000)));
title('初始参考信号时域波形');

%%
% 比相
NN = 15000;

signal_reference_noise = sig(1:NN);
signal_mea_noise = signal12(1:NN);

        
Signal_reference_noise = fft(signal_reference_noise,NN);
Signal_mea_noise = fft(signal_mea_noise,NN);
        
k_reference = find(abs(Signal_reference_noise)==max(abs(Signal_reference_noise)));
k_reference = k_reference (1);
k_mea = find(abs(Signal_mea_noise)==max(abs(Signal_mea_noise)));
k_mea= k_reference;

% 由于MATLAB中自带的atan2函数结果范围在（-pi,pi）之内，因此对于<0 的结果需+2*pi处理
phase_reference_noise = atan2(imag(Signal_reference_noise(k_reference)),real(Signal_reference_noise(k_reference))) ;
phase_reference_noise = phase_reference_noise  + 2*pi*(phase_reference_noise < 0);
phase_mea_noise = atan2(imag(Signal_mea_noise(k_mea)),real(Signal_mea_noise(k_mea)));
phase_mea_noise = phase_mea_noise + 2*pi*(phase_mea_noise < 0);
        
delay = (phase_mea_noise-phase_reference_noise)/(2*pi*f0);
record(pp) = delay;

    hh = hh+1;
    s = sprintf('Simulation in process:%d',ceil(hh*100/T));
    waitbar(hh/T,h,[s '%']);
toc

end

mean(record)
std(record)

figure;
HHHH = histogram(record*1e12);
xlabel('时延/ps');
ylabel('次数');
title('比相偏差统计直方图');



