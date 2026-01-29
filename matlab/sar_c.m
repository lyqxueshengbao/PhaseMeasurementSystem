%  定义了以基准电压Vref，输入信号Vin和精度N作为变量的电荷再分配型SAR ADC sar_c 
function Dout = sar_c(Vref,Vin_parameter,N_parameter) 
sig_c = 1/(16*4096);    %  定义单位电容的标准偏差 
C_nor = [2.^[N_parameter-1:-1:0] 1];    %  定义理想电容阵列,  其中默认电位电容为1
C_dev = sig_c*sqrt(C_nor).*randn(1,N_parameter+1);    %  电容阵列中各电容的标准偏差 
C_act = C_nor + C_dev;    %  定义实际电容由其均值和标准偏差组成 
C_tot = sum(C_act);    %  电容阵列的总电容值 

Vin = double(Vin_parameter);
Vin(~isfinite(Vin)) = 0;
Vin(Vin < 0) = 0;
Vin(Vin > Vref) = Vref;

% 支持标量/向量输入；同一次调用共享同一组电容失配（更符合物理，也更快）
Vs = -Vin(:);    %  在采样保持结束时刻，Vx处的采样保持值Vs = -Vin
code = zeros(size(Vs));
for bitIndex = 1:N_parameter
    Vx = Vs + Vref*C_act(bitIndex)/C_tot;    %  在逐次逼近阶段Vx处的电压值      
    hit = Vx < 0;
    code(hit) = code(hit) + 2^(N_parameter-bitIndex);
    Vs(hit) = Vx(hit);
end

Dout = reshape(code, size(Vin_parameter));    %  将ADC的输出转换成了十进制值以方便处理


% DNL = 1;
% INL = 0.5;
% C_nor = [0 1:1:2^(N_parameter)-1];                      %  理想刻度线是0~2^16-1
% 
% C_act = zeros(1,2^(N_parameter));                       %  实际刻度线
% sum = 0;
% for i=2:2^(N_parameter)
%     distance = 1+ 2*DNL*(rand(1)-1);
%     sum = sum+distance;
%     if(sum<0) 
%         sum=0;
%     end
%     if(abs(sum-i)>INL)
%         if(sum<i) 
%             sum = i-INL;
%         else
%             sum = i+INL;
%         end
%     end
%     C_act(i) = sum;
% end

%V_act = Vref/C_act(2^(N_parameter))*(C_act);            %  转化为实际电压值
% 
% 
% 
% Dout = zeros(1,length(Vin_parameter));
% 
% for i = 1:length(Vin_parameter)      
%     test = Vin_parameter(i);
%     test_number = round(test/(Vref/C_act(2^(N_parameter))));
%     error = Vref;
%     if(test_number -50 <1) 
%         start = 1;
%     else  
%         start = test_number -50;
%     end
%     if(test_number +50 >2^(N_parameter)) 
%         finish = 1; 
%     else  
%         finish = test_number +50;
%     end
% 
%     for j = start:finish
%            if(abs(V_act(j)-test)<error)
%                Dout(i) = V_act(j);
%            end
%     end
% end
end
