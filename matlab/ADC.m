clc;clear


%  产生一个斜坡信号，进行A/D转换并对非线性误差进行分析 
Vref = 2.5;    %  定义基准电压为2.5V 
N = 12;     % 12-bit A/D转换器 



% %  产生斜坡信号，针对其中均匀分布的2N_in个电压值进行处理 
% M = 5;                                      %定义每个转换台阶包含的点数为2^M 
% N_in = N + M; 
% Vin = (0:1:2^N_in-1)/2^N_in*Vref;           %  产生斜坡输入 

%  产生正弦信号
internal = 1/(8e9);
t = 0:internal:(2^16-1)*internal;
f = 2e9;                                    % 信号频率
A = 1;                                      % 信号幅度
fai =  pi/4;                                % 信号初相
Vin = Vref*exp(1i*(2*pi*f*t+fai));          % 参考信号

Vin_i = real(Vin);
Vin_q = imag(Vin);
Vin = Vin + Vref/2;


for i = 1:length(Vin)      
    D(i) = sar_c(Vref,Vin(i),N);     %  针对均匀分布的2N_in个电压值进行A/D转换      
    Do(i) = D(i)*Vref/(2^N);  
end

figure;
plot(Vin(1:100));
hold on;
plot(Do(1:100));

for i = 1:2^N      
     A = (i-1)*ones(1,length(Vin));
     E = D-A;      
     Nm(i) = length(Vin) - nnz(E);      
     DNL(i)=Nm(i)/(length(Vin)/(2^N)) - 1;      
     INL(i) = sum(DNL(1,1:i));    %  计算A/D转换器的DNL和INL      
     DigOut(i) = i-1; 
end

%显示处理结果 
figure;
subplot(2,1,1); 
plot(DigOut,DNL); 
xlabel('Digital Output');
ylabel('DNL(LSBs)'); 
grid on; subplot(2,1,2); 
plot(DigOut,INL); 
xlabel('Digital Output');
ylabel('INL(LSBs)'); % plot(Vin*2^N/Vref,(Vin-Do)*2^N/Vref-1/2);xlabel('Digital output'); ylabel('INL(LSBs)'); 
%  两种计算INL的方法，一种是对DNL进行累积，另外一种是实际曲线与理想曲线的偏差 grid on; 
%  计算有效位数 
sig2 = Vin*Vin';    %  计算输入信号能量 
noi2 = (Vin-Do)*(Vin-Do)';    %  计算噪声能量，这里将输入和输出的差定义成了噪声 
SNDR = 10*log10(sig2/noi2);    %  计算信噪失真比 
ENOB = SNDR/6.02    %  计算A/D转换器的有效位数 



 