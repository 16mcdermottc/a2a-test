FROM golang:1.21-alpine AS builder
WORKDIR /app
COPY go.mod .
COPY . .
RUN go build -o agent main.go

FROM alpine:3.18
WORKDIR /app
COPY --from=builder /app/agent .
CMD ["./agent"]
