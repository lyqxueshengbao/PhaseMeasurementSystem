% clc
% clear all
% close all
% 
% digital_input = repmat(linspace(0,255,100),1,10); % 10周期斜坡信号
% 
% % 含非理想参数的DAC
% [t, sig] = adv_dac_model(digital_input, 12, 5, 1e6,...
%     'INL', 2, ...        % 2 LSB的INL误差
%     'DNL', 0.5, ...      % 0.5 LSB的DNL误差  
%     'jitter', 10e-12, ...% 10ps时钟抖动
%     'noise_rms', 1e-3);  % 1mV热噪声
          

function [t_analog, analog_signal] = adv_dac_model(digital_input, N_bits, Vref, Fs, varargin)
    % 解析可选参数
    p = inputParser;
    addParameter(p, 'INL', 0, @isnumeric);      % 积分非线性误差(LSB)
    addParameter(p, 'DNL', 0, @isnumeric);      % 微分非线性误差(LSB)
    addParameter(p, 'jitter', 0, @isnumeric);   % 时钟抖动标准差(s)
    addParameter(p, 'noise_rms', 0, @isnumeric);% 热噪声有效值(V)
    %addParameter(p, 'psrr', Inf, @isnumeric);   % 电源抑制比(dB)
    %addParameter(p, 'Rout', 0, @isnumeric);     % 输出阻抗(Ohm)
    %addParameter(p, 'Rload', Inf, @isnumeric);  % 负载阻抗(Ohm)
    parse(p, varargin{:});

    % ========== 基础转换 ==========
    Ts = 1/Fs;
    LSB = Vref / (2^N_bits - 1);
    
    % 时间轴抖动处理
    t_digital = (0:length(digital_input)-1)*Ts + p.Results.jitter*randn(1,length(digital_input));
    
    % ========== 非线性误差建模 ==========
    % 生成非线性误差曲线
    inl_error = p.Results.INL * LSB * (2*rand(1,2^N_bits)-1); % 随机INL误差
    dnl_error = p.Results.DNL * LSB * (randn(1,2^N_bits));     % 随机DNL误差
    error_lut = inl_error + cumsum(dnl_error);                 % 总误差查找表
    
    % 应用非线性误差（防止索引越界）
    clamped_input = min(max(round(digital_input),0),2^N_bits-1)+1;    % +1调整为MATLAB索引
    analog_values = (digital_input * LSB) + error_lut(clamped_input);
    
    % ========== 高分辨率插值 ==========
    R = 4; % 上采样因子
    t_analog = linspace(min(t_digital), max(t_digital), length(digital_input)*R);
    analog_signal = interp1(t_digital, analog_values, t_analog);

    % ========== 热噪声注入 ==========
    analog_signal = analog_signal + p.Results.noise_rms*randn(size(analog_signal));

    analog_signal = analog_signal(1:R:4*(length(digital_input)-1)+1);
    
    % ========== 可视化 ==========
    % figure;
    % subplot(2,1,1);
    % stem(t_digital(1:100), analog_values(1:100), 'r', 'MarkerSize', 4); 
    % hold on;
    % plot(t_analog(1:100*R), analog_signal(1:100*R), 'b');
    % title('时域波形');
    % legend('理想采样值','实际输出');
    % grid on;
    % 
    % subplot(2,1,2);
    % [pxx,f] = pwelch(analog_signal-mean(analog_signal), [],[],[],Fs*R);
    % semilogx(f,10*log10(pxx));
    % title('噪声功率谱密度');
    % xlabel('频率 (Hz)');
    % ylabel('PSD (dB/Hz)');
    % grid on;
end