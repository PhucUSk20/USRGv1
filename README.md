# To install the PROJECT
You need create DATABASE
-----------------------------------------------------------------------
    CREATE TABLE Person (
    ID INT PRIMARY KEY IDENTITY(1,1),
    Name NVARCHAR(255), 
    FaceVector VARBINARY(MAX), -- Dữ liệu biểu đồ khuôn mặt
    ImageData VARBINARY(MAX) 
    );
