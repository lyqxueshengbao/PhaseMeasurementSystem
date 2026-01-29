function ENOB = adc_analysis(N,Vin,Do,D)
% N为分辨率，单位bit
% Vin为模拟信号
% Do为ADC采样后的数字信号

level_count = 2^N;
D = double(D(:));
D(~isfinite(D)) = 0;
D = round(D);
D(D < 0) = 0;
D(D > level_count - 1) = level_count - 1;

% 统计码型出现次数（用于DNL/INL等分析时更快）
% Nm(i) 表示数字码 (i-1) 的出现次数
Nm = accumarray(D + 1, 1, [level_count, 1], @sum, 0).';

% DNL/INL
DNL = Nm/(numel(D)/level_count) - 1;
INL = cumsum(DNL);
DigOut = 0:(level_count-1);

%显示处理结果 
% figure;
% subplot(2,1,1); 
% plot(DigOut,DNL); 
% xlabel('Digital Output');
% ylabel('DNL(LSBs)'); 
% grid on; subplot(2,1,2); 
% plot(DigOut,INL); 
% xlabel('Digital Output');
% ylabel('INL(LSBs)');  

% 计算有效位数
Vin = Vin(:);
Do = Do(:);
sig2 = Vin' * Vin;                                         %  计算输入信号能量 
noi2 = (Vin - Do)' * (Vin - Do);                           %  计算噪声能量，这里将输入和输出的差定义成了噪声
SNDR = 10*log10(sig2/noi2);                                %  计算信噪失真比
ENOB = SNDR/6.02;                                          %  计算A/D转换器的有效位数 
end
