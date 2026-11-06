# RMI đồng bộ ghi chú trên 2 server

Project dùng Java RMI và JavaFX. Mỗi máy server mở một cửa sổ riêng để nhập tên, địa chỉ của mình và địa chỉ máy kia. Mỗi máy client cũng mở một cửa sổ riêng để xem, thêm, sửa, xóa ghi chú chung.

## Yêu cầu

- JDK 17 trở lên và Maven 3.8 trở lên. File `.bat` cũng có thể dùng JDK/Maven đi kèm IntelliJ.
- Các máy nhìn thấy nhau qua mạng LAN. Nếu client chạy trong máy ảo, cấu hình mạng để máy ảo truy cập được IP của hai server.
- Trên mỗi server, mở firewall cho cổng registry đã nhập và cổng kế tiếp. Ví dụ: TCP `1099` và `1100`.

## Chạy thử trên một máy

Mở `run-server-gui.bat` **hai lần**:

| Cửa sổ | Tên server | Địa chỉ máy này | Địa chỉ máy kia |
|---|---|---|---|
| 1 | Server máy 1 | `127.0.0.1:1099` | `127.0.0.1:2099` |
| 2 | Server máy 2 | `127.0.0.1:2099` | `127.0.0.1:1099` |

Bấm **Chạy server** ở cả hai cửa sổ. Khi kết nối thành công, mỗi cửa sổ hiện tên máy kia và trạng thái **Đã kết nối**.

Mở hai client riêng:

```bat
run-client.bat 127.0.0.1 1099
run-client.bat 127.0.0.1 2099
```

Mỗi lệnh mở một cửa sổ client và tự kết nối tới server đã chỉ định. Cũng có thể nhấp đúp `run-client.bat` rồi nhập IP/cổng trong giao diện.

## Chạy trên hai máy server thật

Ví dụ IP máy 1 là `192.168.1.10`, IP máy 2 là `192.168.1.11`. Chép cùng project lên hai máy và mở `run-server-gui.bat` trên **mỗi máy**:

| Máy | Tên server | Địa chỉ máy này | Địa chỉ máy kia |
|---|---|---|---|
| 1 | Server máy 1 | `192.168.1.10:1099` | `192.168.1.11:1099` |
| 2 | Server máy 2 | `192.168.1.11:1099` | `192.168.1.10:1099` |

Bấm **Chạy server** trên cả hai máy. Ô **KẾT NỐI MÁY KIA** cho biết đã kết nối hay mất kết nối, tên peer và lỗi nếu có. Các chỉ số thao tác, số ghi chú, lần đồng bộ và nhật ký cũng hiện trên từng máy.

Trên **client máy 1** chạy `run-client.bat 192.168.1.10 1099`. Trên **client máy 2** chạy `run-client.bat 192.168.1.11 1099`. Nếu client là máy ảo, chạy lệnh trong từng máy ảo. Thay IP ví dụ bằng IP LAN thực tế của bạn.

## Trình diễn đồng bộ

1. Client máy 1 thêm ghi chú. Client máy 2 sẽ thấy ghi chú đó.
2. Client máy 2 sửa hoặc xóa ghi chú. Client máy 1 sẽ thấy thay đổi.
3. Dừng một server, thêm ghi chú qua server còn lại, rồi chạy lại server đã dừng. Dữ liệu được đồng bộ lại.

Client tải danh sách mỗi 2 giây; hai server đồng bộ định kỳ mỗi 3 giây. Dữ liệu được lưu trong `data/server-<cổng>.ser` trên **từng máy**. Bản ghi đã xóa vẫn được lưu nội bộ để việc xóa được đồng bộ. Nếu cùng một ghi chú bị sửa trên hai server khi mất mạng, bản có phiên bản lớn hơn được giữ; nếu bằng nhau, địa chỉ server quyết định.

## Kiểm tra tự động

Khi đã đóng cả hai server, chạy lệnh sau để kiểm tra tên server qua RMI, đồng bộ và trạng thái kết nối/mất kết nối trên localhost:

```powershell
mvn test-compile exec:java '-Dexec.mainClass=vn.edu.rmi.ServerMonitorCheck' '-Dexec.classpathScope=test'
```

Ví dụ này dành cho mạng LAN tin cậy; kết nối RMI chưa có đăng nhập hoặc mã hóa.
