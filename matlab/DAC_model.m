function Dout = DAC_model(Vref,Vin_parameter,N_parameter,DNL,INL,Fs,F_start,F_end) 

% clc;clear
% Vin_parameter = [0:0.05:2,2:-0.05:0,0:0.05:2];  
% Vref = 3;
% N_parameter=16;
% DNL = 1.5;

% 生成 DAC 非线性传输特性（同一组参数下进行缓存，避免重复随机生成导致的超长仿真时间）
persistent last_key last_v_act_u
key = sprintf('V=%.16g_N=%d_DNL=%.16g_INL=%.16g', Vref, N_parameter, DNL, INL);

if isempty(last_key) || ~strcmp(last_key, key)
    level_count = 2^(N_parameter);

    c_act = zeros(1, level_count);                       %  实际刻度线
    accum = 0;
    for k = 2:level_count
        distance = 1 + 2*DNL*(rand(1)-1);
        accum = accum + distance;
        if accum < 0
            accum = 0;
        end
        if abs(accum - k) > INL
            if accum < k
                accum = k - INL;
            else
                accum = k + INL;
            end
        end
        c_act(k) = accum;
    end

    if c_act(end) == 0
        error('DAC_model:InvalidTransfer', 'Invalid DAC transfer: c_act(end)=0. Check DNL/INL.');
    end

    v_act = Vref/c_act(end) * c_act;                     %  转化为实际电压值
    if any(diff(v_act) <= 0)
        for idx = 2:level_count
            if v_act(idx) <= v_act(idx-1)
                v_act(idx) = v_act(idx-1) + eps(v_act(idx-1));
            end
        end
    end
    last_v_act_u = unique(v_act, 'stable');              %  interp1 要求 x 单调且尽量无重复
    last_key = key;
end

vin = real(double(Vin_parameter));
vin(~isfinite(vin)) = 0;
vin(vin < 0) = 0;
vin(vin > Vref) = Vref;

% 对每个输入点取最近的 DAC 输出电平（矢量化替代双重for循环）
Dout = interp1(last_v_act_u, last_v_act_u, vin, 'nearest', 'extrap');

% figure;
% plot(Vin_parameter(1:200),'b*');
% hold on;
% plot(Dout(1:200));
% 
% figure;
% real_sig_tmp1 = real (Dout);
% real_sig1 = floor (length(Vin_parameter)   * real_sig_tmp1);
% plot(linspace(-Fs/2,Fs/2,length(Vin_parameter)),db(fftshift(fft(real_sig1))));
% title('主站发射路混频器输出频谱');

% DAC平滑输出
% Dout = Filter(Dout,length(Vin_parameter),Fs,F_start,F_end);

end
